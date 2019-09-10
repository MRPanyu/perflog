package perflog;

import java.io.Serializable;

class PerfStepStatistics implements Serializable {

	private static final long serialVersionUID = 1L;

	private String logName;
	private String stepName;
	private long executeCount;
	private long maxUseTime;
	private long maxUseTimeExceptSubSteps;
	private long totalUseTime;
	private long totalUseTimeExceptSubSteps;

	public PerfStepStatistics(String logName, String stepName) {
		this.logName = logName;
		this.stepName = stepName;
	}

	public synchronized void mergeStep(PerfStep perfStep) {
		this.executeCount++;
		long useTime = perfStep.getUseTime();
		long useTimeExceptSubSteps = perfStep.getUseTimeExcludeSubSteps();
		this.totalUseTime += useTime;
		this.totalUseTimeExceptSubSteps += useTimeExceptSubSteps;
		if (useTime > this.maxUseTime) {
			this.maxUseTime = useTime;
		}
		if (useTimeExceptSubSteps > this.maxUseTimeExceptSubSteps) {
			this.maxUseTimeExceptSubSteps = useTimeExceptSubSteps;
		}
	}

	public String getLogName() {
		return logName;
	}

	public String getStepName() {
		return stepName;
	}

	public long getExecuteCount() {
		return executeCount;
	}

	public void setExecuteCount(long executeCount) {
		this.executeCount = executeCount;
	}

	public long getTotalUseTime() {
		return totalUseTime;
	}

	public void setTotalUseTime(long totalUseTime) {
		this.totalUseTime = totalUseTime;
	}

	public long getTotalUseTimeExceptSubSteps() {
		return totalUseTimeExceptSubSteps;
	}

	public void setTotalUseTimeExceptSubSteps(long totalUseTimeExceptSubSteps) {
		this.totalUseTimeExceptSubSteps = totalUseTimeExceptSubSteps;
	}

	public long getMaxUseTime() {
		return maxUseTime;
	}

	public void setMaxUseTime(long maxUseTime) {
		this.maxUseTime = maxUseTime;
	}

	public long getMaxUseTimeExceptSubSteps() {
		return maxUseTimeExceptSubSteps;
	}

	public void setMaxUseTimeExceptSubSteps(long maxUseTimeExceptSubSteps) {
		this.maxUseTimeExceptSubSteps = maxUseTimeExceptSubSteps;
	}

}
