package io.chandler.gap.morajai;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jocl.CL;
import org.jocl.Pointer;
import org.jocl.Sizeof;
import org.jocl.cl_command_queue;
import org.jocl.cl_context;
import org.jocl.cl_context_properties;
import org.jocl.cl_device_id;
import org.jocl.cl_kernel;
import org.jocl.cl_mem;
import org.jocl.cl_platform_id;
import org.jocl.cl_program;
import org.jocl.cl_queue_properties;

import io.chandler.gap.morajai.MoraJaiBox.Color;

public class MJAnalysisGPU {

	private static boolean QUIET = true;

	private final boolean noBlue;
	private final Path storageDir;

	public MJAnalysisGPU(Path storageDir, boolean noBlue) {
		this.noBlue = noBlue;
		this.storageDir = storageDir;
	}

	public String stateToJson(Color[] targetColors, int state) {
		MoraJaiBox box = new MoraJaiBox();
		box.initFromState(targetColors, state);
		StringJoiner joiner = new StringJoiner(", ");
		for (int i = 0; i < 9; i++) {
			joiner.add("\"" + box.getTileColor(i).name().replace("C_", "") + "\"");
		}
		return "[" + joiner.toString() + "]";
	}

	private void set(long[] depths, int state) {
		int idx = state >> 6;             // state / 64
		depths[idx] |= 1L << (state & 63);
	}

	private boolean isSet(long[] depths, int state) {
		int idx = state >> 6;
		return (depths[idx] & (1L << (state & 63))) != 0;
	}

	private static final Object initMonitor = new Object();
	private static boolean initInitiated = false;

	private static final HashMap<Integer, ArrayList<Long>> workGroupTimes = new HashMap<>();

	private static final AtomicBoolean haltForCalculations = new AtomicBoolean(false);
	private static final AtomicInteger workGroupSize = new AtomicInteger(0);

	private ExecutorService pruneExecutor = null;
	public void enablePrune(ExecutorService pruneExecutor) {
		this.pruneExecutor = pruneExecutor;
	}

	public void fullDepthAnalysis(Color[] targetColors, int idx, Consumer<MJAnalysisStats> statsUpdate) {
		boolean probeBestTime = false;
		synchronized (initMonitor) {
			if (!initInitiated) {
				initInitiated = true;
				probeBestTime = true;
				haltForCalculations.set(true);
			}
		}

		while (!probeBestTime && haltForCalculations.get()) {
			try {
				synchronized (initMonitor) {
					initMonitor.wait(1000);
				}
			} catch (InterruptedException e) {
			}
		}

		String filename = noBlue ? "_noBlue" : "";
		for (Color color : targetColors) {
			if (noBlue && color == C_BU) return;
			filename += "_" + color.name();
		}
		MJAnalysisStats stats = new MJAnalysisStats(idx, filename);
		statsUpdate.accept(stats);

		cl_context context = null;
		cl_command_queue commandQueue = null;
		cl_program program = null;
		cl_kernel kernel = null;
		cl_mem memObjects[] = new cl_mem[3];

		try {
			// Initialize OpenCL
			final int platformIndex = 0;
			final long deviceType = CL.CL_DEVICE_TYPE_ALL;
			final int deviceIndex = 0;
			CL.setExceptionsEnabled(true);
			int numPlatformsArray[] = new int[1];
			CL.clGetPlatformIDs(0, null, numPlatformsArray);
			int numPlatforms = numPlatformsArray[0];
			cl_platform_id platforms[] = new cl_platform_id[numPlatforms];
			CL.clGetPlatformIDs(platforms.length, platforms, null);
			cl_platform_id platform = platforms[platformIndex];
			cl_context_properties contextProperties = new cl_context_properties();
			contextProperties.addProperty(CL.CL_CONTEXT_PLATFORM, platform);
			int numDevicesArray[] = new int[1];
			CL.clGetDeviceIDs(platform, deviceType, 0, null, numDevicesArray);
			int numDevices = numDevicesArray[0];
			cl_device_id devices[] = new cl_device_id[numDevices];
			CL.clGetDeviceIDs(platform, deviceType, numDevices, devices, null);
			cl_device_id device = devices[deviceIndex];

			
			context = CL.clCreateContext(
				contextProperties, 1, new cl_device_id[]{device}, 
				null, null, null);
			commandQueue = CL.clCreateCommandQueueWithProperties(context, device, new cl_queue_properties(), null);
			String programSource;
			try (InputStream is = MJAnalysisGPU.class.getResourceAsStream("/morajai/mj_kernel.cl")) {
				programSource = new String(is.readAllBytes());
			} catch (IOException e) {
				e.printStackTrace();
				return;
			}
			program = CL.clCreateProgramWithSource(context, 1, new String[]{programSource}, null, null);
			CL.clBuildProgram(program, 0, null, null, null, null);
			kernel = CL.clCreateKernel(program, "mj_solve", null);

			try (PrintStream out = new PrintStream(new File(storageDir.resolve("depths_v2_" + idx + filename + ".txt").toString()))) {
				out.println("Starting analysis for " + idx + " " + filename + " with GPU - pruner: " + (pruneExecutor != null));
				long[] reached = new long[1000000000 / 64];
				long[] current = new long[1000000000 / 64];
				long[] next = new long[1000000000 / 64];
				int counter = generateDepth0(targetColors, current);
				stats.depth = 0;
				stats.statesAtDepth = counter;
				stats.begun = true;
				statsUpdate.accept(stats);

				if (pruneExecutor != null) {
					stats.pruning = true;
					statsUpdate.accept(stats);
					int prunedDead = MJColorPrune.prune(pruneExecutor, noBlue, targetColors, (state) -> {
						set(reached, state);
					});
					stats.initalPruned = prunedDead;
					stats.dead = prunedDead;
					stats.pruning = false;
					statsUpdate.accept(stats);
				}

				int counterAccum = 0;
				int depth = 0;

				memObjects[0] = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE, reached.length * Sizeof.cl_long, null, null);
				memObjects[1] = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE, current.length * Sizeof.cl_long, null, null);
				memObjects[2] = CL.clCreateBuffer(context, CL.CL_MEM_READ_WRITE, next.length * Sizeof.cl_long, null, null);
				CL.clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memObjects[0]));
				CL.clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memObjects[1]));
				CL.clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(memObjects[2]));

				while (counter > 0) {
					counterAccum += counter;
					stats.unreached = 1_000_000_000 - counterAccum - stats.dead;
					stats.depth = depth;
					stats.statesAtDepth = counter;
					statsUpdate.accept(stats);
					//System.out.println("Depth " + depth + " has " + counter + " states");
					out.println("Depth " + depth + " has " + counter + " states");
					counter = 0;
					depth++;

					int workGroupSize = MJAnalysisGPU.workGroupSize.get();

					Queue<Integer> workGroupSizes = new LinkedList<>();
					
					if (probeBestTime) {
						// Query and print device information
						long[] maxWorkGroupSize = new long[1];
						CL.clGetDeviceInfo(device, CL.CL_DEVICE_MAX_WORK_GROUP_SIZE, Sizeof.size_t, Pointer.to(maxWorkGroupSize), null);
						if (!QUIET) System.out.println("Max work group size for device: " + maxWorkGroupSize[0]);
						for (int i = (int)maxWorkGroupSize[0]; i >= 64; i /= 2) {
							workGroupSizes.add(i);
						}
						probeBestTime = true;
					} else {
						workGroupSizes.add(workGroupSize);
					}

					long[] nextClone = null;

					while (!workGroupSizes.isEmpty()) {
						int WGS = workGroupSizes.poll();

						if (probeBestTime) {
							nextClone = next.clone();
						}

						boolean success = false;
						long time = System.currentTimeMillis();
					
						try {
							// Update device buffers
							CL.clEnqueueWriteBuffer(commandQueue, memObjects[0], CL.CL_TRUE, 0, reached.length * Sizeof.cl_long, Pointer.to(reached), 0, null, null);
							CL.clEnqueueWriteBuffer(commandQueue, memObjects[1], CL.CL_TRUE, 0, current.length * Sizeof.cl_long, Pointer.to(current), 0, null, null);
							CL.clEnqueueFillBuffer(commandQueue, memObjects[2], Pointer.to(new byte[]{0}), 1, 0, next.length * Sizeof.cl_long, 0, null, null);
							

							// Execute kernel
							int gpuChunkSize = 1_000_000_000;
							for (int i = 0; i < 1_000_000_000; i += gpuChunkSize) {
								CL.clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[]{i}));
								long global_work_size[] = new long[]{gpuChunkSize};
								long local_work_size[] = new long[]{WGS};
								CL.clEnqueueNDRangeKernel(commandQueue, kernel, 1, null, global_work_size, local_work_size, 0, null, null);
							}
							
							// Read back results
							CL.clEnqueueReadBuffer(commandQueue, memObjects[2], CL.CL_TRUE, 0, next.length * Sizeof.cl_long, Pointer.to(next), 0, null, null);

							success = true;
						} catch (RuntimeException e) {
							if (!QUIET) System.out.println("Error: " + e.getMessage() + " with WGS: " + WGS);
							if (!probeBestTime) {
								throw e;
							}
						}

						time = System.currentTimeMillis() - time;

						if (success && probeBestTime) {
							if (!QUIET) System.out.println("Time: " + time + " with WGS: " + WGS);
							ArrayList<Long> times = workGroupTimes.get(WGS);
							if (times == null) {
								times = new ArrayList<>();
								workGroupTimes.put(WGS, times);
							}
							times.add(time);
							if (times.size() < 3) {
								// Add another to queue
								workGroupSizes.add(WGS);
							}
							next = nextClone;
							nextClone = null;
							
							// Resolve results
							long minTime = Integer.MAX_VALUE;
							int minWGS = 0;
							if (workGroupSizes.isEmpty()) {
								for (Map.Entry<Integer, ArrayList<Long>> entry : workGroupTimes.entrySet()) {
									times = entry.getValue();
									if (times == null || times.isEmpty()) continue;
									// average
									long avg = 0;
									for (long t : times) avg += t / times.size();
									if (avg < minTime) {
										minTime = avg;
										minWGS = entry.getKey();
									}
								}

								if (minWGS == 0) throw new RuntimeException("No best WGS found");
								
								if (!QUIET) System.out.println("Best time: " + minTime + " with WGS: " + minWGS);
								// That was the last test - revert back to normal operation
								workGroupSize = minWGS;
								MJAnalysisGPU.workGroupSize.set(minWGS);
								probeBestTime = false;
								workGroupSizes.add(minWGS); // Setup actual processing
								synchronized (initMonitor) {
									haltForCalculations.set(false);
									initMonitor.notifyAll();
								}
							}
						}
					}

					
					// Count 'set' bits in next array
					for (int i = 0; i < 1000000000; i++) {
						if (isSet(next, i)) {
							counter++;
						}
					}
					//System.out.println("Counted " + counter + " states");
					
					if (counter < 180) {
						for (int i = 0; i < 1000000000; i++) {
							if (isSet(next, i)) {
								out.println(stateToJson(targetColors, i));
							}
						}
					}
					if (counter > 0) {
						for (int i = 0; i < reached.length; i++) {
							reached[i] |= current[i];
							current[i] = next[i];
							next[i] = 0L;
						}
					}
				}
				stats.complete = true;
				out.println("Complete");
				statsUpdate.accept(stats);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} finally {
			if (kernel != null) CL.clReleaseKernel(kernel);
			for (int i = 0; i < memObjects.length; i++) {
				if (memObjects[i] != null) CL.clReleaseMemObject(memObjects[i]);
			}
			if (program != null) CL.clReleaseProgram(program);
			if (commandQueue != null) CL.clReleaseCommandQueue(commandQueue);
			if (context != null) CL.clReleaseContext(context);
		}
	}

	private int generateDepth0(MoraJaiBox.Color[] targetColors, long[] depths) {
		int counter = 0;
		MoraJaiBox.Color[] initColors = new MoraJaiBox.Color[9];
		initColors[0] = targetColors[0];
		initColors[2] = targetColors[1];
		initColors[8] = targetColors[2];
		initColors[6] = targetColors[3];

		MoraJaiBox box = new MoraJaiBox();

		for (int i = 0; i < 100000; i++) {
			int recomp = 0;
			int decomp = i;
			for (int j = 0; j < 9; j++) {
				if (j == 0 || j == 2 || j == 6 || j == 8) {}
				else {
					initColors[j] = MoraJaiBox.COLOR_VALUES[decomp % 10];
					decomp /= 10;
				}
				// TODO check this
				recomp += initColors[j].ordinal() * Math.pow(10, j);
			}

			box.initFromState(targetColors, recomp);
			if (box.areInnerMatchingOuter()) {
				set(depths, box.getState());
				counter++;
			}
		}
		return counter;
	}

	public static void main(String[] args) throws InterruptedException {

		QUIET = false;
		long startTime = System.currentTimeMillis();

		ExecutorService executor = Executors.newFixedThreadPool(3);
		ExecutorService pruneExecutor = Executors.newWorkStealingPool(16);

		for (int i = 0; i <3; i++) {
			final int idx = i;
			executor.submit(() -> {
				try {
					MJAnalysisGPU analysis = new MJAnalysisGPU(Paths.get("morajai_gpu_depths"), false);
					if (idx > 0) analysis.enablePrune(pruneExecutor);
					analysis.fullDepthAnalysis(new Color[] {C_PI, C_PI, C_PI, C_PI}, 5555, (stats) -> {
						System.out.println(stats.filename + " " + stats.depth + " " + stats.statesAtDepth + " " + stats.unreached);
					});
				} catch (Exception e) {
					e.printStackTrace();
				}
			});
			Thread.sleep(100);
		}

		executor.shutdown();
		executor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);

		long endTime = System.currentTimeMillis();
		
		System.out.println("Time taken: " + (endTime - startTime) + "ms");
	}


}
