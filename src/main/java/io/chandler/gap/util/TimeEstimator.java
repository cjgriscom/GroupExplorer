package io.chandler.gap.util;

public class TimeEstimator {
	private long startTime;
	private long totalCombinations;

	public TimeEstimator(long totalCombinations) {
		this.startTime = System.currentTimeMillis();
		this.totalCombinations = totalCombinations;
	}


    public synchronized void checkProgressEstimate(long currentIteration, int results) {
        checkProgressEstimate(currentIteration, (long) results);
    }

    public synchronized void checkProgressEstimate(long currentIteration, Long results) {
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        long estimatedTotalTime = (long) ((double) elapsedTime / currentIteration * totalCombinations);
        long remainingTime = estimatedTotalTime - elapsedTime;
        
        String remainingTimeStr = String.format("%d hours, %d minutes, %d seconds",
            remainingTime / 3600000,
            (remainingTime % 3600000) / 60000,
            (remainingTime % 60000) / 1000);
        
        System.out.println(currentIteration + " / " + totalCombinations + (results != null ? " -> " + results : "") +
            " | Estimated time remaining: " + remainingTimeStr);

    }


    public int checkQuit() {
        try {
            // Check for key press
            while (System.in.available() > 0) {
                int i = System.in.read();
                if (i == 'q' || i == 'Q') {
                    return -1;
                }
            }
        } catch (Exception e) {}
        return 0;
    }
}
