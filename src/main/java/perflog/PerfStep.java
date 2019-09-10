package perflog;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 性能日志中某个步骤的相关数据
 * 
 * @author panyu
 *
 */
class PerfStep implements Serializable {

	private static final long serialVersionUID = 1L;

	private String logName;
	private String stepName;
	private long startTime;
	private long endTime;

	private PerfStep parentStep;
	private List<PerfStep> subSteps = new ArrayList<PerfStep>();

	public PerfStep(String logName, String stepName) {
		this(logName, stepName, System.currentTimeMillis());
	}

	public PerfStep(String logName, String stepName, long startTime) {
		this.logName = logName;
		this.stepName = stepName;
		this.startTime = startTime;
	}

	public void end() {
		this.endTime = System.currentTimeMillis();
	}

	public void addSubStep(PerfStep perfStep) {
		subSteps.add(perfStep);
		perfStep.setParentStep(this);
	}

	public String getLogName() {
		return logName;
	}

	public String getStepName() {
		return stepName;
	}

	public long getStartTime() {
		return startTime;
	}

	public void setStartTime(long startTime) {
		this.startTime = startTime;
	}

	public long getEndTime() {
		return endTime;
	}

	public void setEndTime(long endTime) {
		this.endTime = endTime;
	}

	public List<PerfStep> getSubSteps() {
		return subSteps;
	}

	public PerfStep getParentStep() {
		return parentStep;
	}

	public void setParentStep(PerfStep parentStep) {
		this.parentStep = parentStep;
	}

	public boolean isEnded() {
		return endTime > 0;
	}

	public long getUseTime() {
		return endTime - startTime;
	}

	public long getUseTimeExcludeSubSteps() {
		long useTime = getUseTime();
		long subStepUseTime = 0;
		for (PerfStep subStep : subSteps) {
			subStepUseTime += subStep.getUseTime();
		}
		return useTime - subStepUseTime;
	}

}
