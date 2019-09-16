package perflog;

/**
 * 性能日志记录工具类
 * 
 * @author panyu
 *
 */
public class PerfLog {

	private static ThreadLocal<String> tlLogName = new ThreadLocal<String>();
	private static ThreadLocal<PerfStep> tlRootStep = new ThreadLocal<PerfStep>();
	private static ThreadLocal<PerfStep> tlCurrentStep = new ThreadLocal<PerfStep>();

	/**
	 * 在当前线程中设置标记，开始记录性能日志。
	 * <p>
	 * 如果当前线程中没有设置为开始记录性能日志，后续即使调用 {@link #startStep(String)} 或
	 * {@link #endStep(String)}
	 * ，也会被忽略。这样可以让你在一些比较公用的程序中也添加性能日志，而不用担心其他无关的线程程序中执行到这段公用程序而被记录下来，影响整体数据。
	 * <p>
	 * 注意当前线程中同时只能开启一次性能日志，线程中已经开启性能日志的情况下再开启一个名称不同的日志，后一个不会记录。
	 * 
	 * @param logName 日志名称。
	 */
	public static void startLog(String logName) {
		if (tlLogName.get() != null) {
			return;
		}
		tlLogName.set(logName);
		tlRootStep.remove();
		tlCurrentStep.remove();
	}

	/**
	 * 在当前线程中设置标记，结束性能日志记录
	 * 
	 * @param logName 日志名称，与开始时填写的日志名称需对应，否则不会结束
	 * @see #startLog(String)
	 */
	public static void endLog(String logName) {
		String currentLogName = tlLogName.get();
		if (currentLogName == null || !currentLogName.equals(logName)) {
			return;
		}
		PerfStep rootStep = tlRootStep.get();
		if (rootStep != null) {
			PerfLogWriter.append(rootStep);
		}
		tlLogName.remove();
		tlRootStep.remove();
		tlCurrentStep.remove();
	}

	/**
	 * 记录某个步骤开始
	 * 
	 * @param stepName 步骤名称
	 */
	public static void startStep(String stepName) {
		String logName = tlLogName.get();
		if (logName == null) {
			return;
		}
		PerfStep perfStep = new PerfStep(logName, stepName);
		PerfStep parentStep = tlCurrentStep.get();
		if (parentStep == null) {
			tlRootStep.set(perfStep);
		} else {
			parentStep.addSubStep(perfStep);
		}
		tlCurrentStep.set(perfStep);
	}

	/**
	 * 记录某个步骤结束
	 * 
	 * @param stepName 步骤名称
	 */
	public static void endStep(String stepName) {
		String logName = tlLogName.get();
		if (logName == null) {
			return;
		}
		PerfStep currentStep = tlCurrentStep.get();
		if (currentStep == null) {
			throw new RuntimeException("Calling endStep without startStep, stepName=" + stepName);
		} else if (stepName != currentStep.getStepName()) {
			throw new RuntimeException("Step name not equal to current step, maybe last step is unended? stepName="
					+ stepName + ", currentStepName=" + currentStep.getStepName());
		}
		currentStep.end();
		tlCurrentStep.set(currentStep.getParentStep());
	}

}
