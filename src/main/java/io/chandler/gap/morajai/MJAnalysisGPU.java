package io.chandler.gap.morajai;

import static io.chandler.gap.morajai.MoraJaiBox.Color.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.StringJoiner;
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

	private final boolean noBlue;

	public MJAnalysisGPU(boolean noBlue) {
		this.noBlue = noBlue;
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

	public void fullDepthAnalysis(Color[] targetColors, int idx, Consumer<MJAnalysisStats> statsUpdate) {

		String filename = noBlue ? "noBlue" : "";
		for (Color color : targetColors) {
			if (noBlue && color == C_BU) return;
			filename += "_" + color.name();
		}
		MJAnalysisStats stats = new MJAnalysisStats(idx, filename);
		statsUpdate.accept(stats);


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
		
		cl_context context = CL.clCreateContext(
			contextProperties, 1, new cl_device_id[]{device}, 
			null, null, null);
		        
		cl_command_queue commandQueue = 
			CL.clCreateCommandQueueWithProperties(context, device, new cl_queue_properties(), null);

		// Load kernel source
		String programSource;
		try (InputStream is = MJAnalysisGPU.class.getResourceAsStream("/morajai/mj_kernel.cl")) {
			programSource = new String(is.readAllBytes());
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		// Create and build program
		cl_program program = CL.clCreateProgramWithSource(context,
			1, new String[]{ programSource }, null, null);
		CL.clBuildProgram(program, 0, null, null, null, null);
		
		
		try (PrintStream out = new PrintStream(new File("morajai_gpu_depths/depths_v2_" + filename + ".txt"))) {

			
			long[] reached    = new long[1000000000/64];
			long[] current    = new long[1000000000/64];
			long[] next       = new long[1000000000/64];

			// Loop through and mark each zero state
			int depth = 0;
			int counter = generateDepth0(targetColors, current);
			stats.depth = 0;
			stats.statesAtDepth = counter;
			statsUpdate.accept(stats);

			int counterAccum = 0;

			while (counter > 0) {

				counterAccum += counter;

				int remainingStates = 1_000_000_000 - counterAccum;

				stats.unreached = remainingStates;
				stats.depth = depth;
				stats.statesAtDepth = counter;
				

				// Send update for depth
				statsUpdate.accept(stats);

				System.out.println("Depth " + depth + " has " + counter + " states");
				out.println("Depth " + depth + " has " + counter + " states");
				counter = 0;
				depth++;
				
		        // Create kernel
		        cl_kernel kernel = CL.clCreateKernel(program, "mj_solve", null);

		        // Create memory buffers
		        cl_mem memObjects[] = new cl_mem[3];
		        memObjects[0] = CL.clCreateBuffer(context, 
		            CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
		            reached.length * Sizeof.cl_long, Pointer.to(reached), null);
		        memObjects[1] = CL.clCreateBuffer(context, 
		            CL.CL_MEM_READ_ONLY | CL.CL_MEM_COPY_HOST_PTR,
		            current.length * Sizeof.cl_long, Pointer.to(current), null);
		        memObjects[2] = CL.clCreateBuffer(context, 
		            CL.CL_MEM_READ_WRITE, 
		            next.length * Sizeof.cl_long, null, null);
		        
		        // Set kernel arguments
		        CL.clSetKernelArg(kernel, 0, Sizeof.cl_mem, Pointer.to(memObjects[0]));
		        CL.clSetKernelArg(kernel, 1, Sizeof.cl_mem, Pointer.to(memObjects[1]));
		        CL.clSetKernelArg(kernel, 2, Sizeof.cl_mem, Pointer.to(memObjects[2]));

				System.out.println("Set kernel arguments");

		        // Execute kernel
		        int gpuChunkSize = 100_000_000;
		        for (int i = 0; i < 1_000_000_000; i+= gpuChunkSize) {
		        	CL.clSetKernelArg(kernel, 3, Sizeof.cl_int, Pointer.to(new int[] {i}));
			        long global_work_size[] = new long[]{gpuChunkSize};
			        long local_work_size[] = new long[]{256};
			        CL.clEnqueueNDRangeKernel(commandQueue, kernel, 1, null,
			            global_work_size, local_work_size, 0, null, null);
		        }
		        
		        // Read back results
		        CL.clEnqueueReadBuffer(commandQueue, memObjects[2], CL.CL_TRUE, 0,
		            next.length * Sizeof.cl_long, Pointer.to(next), 0, null, null);

				System.out.println("Read back results");

		        // Count 'set' bits in next array
				counter = 0;
				for (int i = 0; i < 1000000000; i++) {
					if (isSet(next, i)) {
						counter++;
					}
				}

				System.out.println("Counted " + counter + " states");

		        // Release OpenCL resources
		        CL.clReleaseMemObject(memObjects[0]);
		        CL.clReleaseMemObject(memObjects[1]);
		        CL.clReleaseMemObject(memObjects[2]);
		        CL.clReleaseKernel(kernel);
				

				if (counter < 100) {
					// Loop through depthsNext true entries and print the state to json
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
			statsUpdate.accept(stats);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			CL.clReleaseProgram(program);
			CL.clReleaseCommandQueue(commandQueue);
			CL.clReleaseContext(context);
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

	public static void main(String[] args) {
		long startTime = System.currentTimeMillis();

		MJAnalysisGPU analysis = new MJAnalysisGPU(false);
		analysis.fullDepthAnalysis(new Color[] {C_PI, C_PI, C_PI, C_PI}, 5555, (stats) -> {
			System.out.println(stats.filename + " " + stats.depth + " " + stats.statesAtDepth + " " + stats.unreached);
		});

		long endTime = System.currentTimeMillis();
		System.out.println("Time taken: " + (endTime - startTime) + "ms");
	}


}
