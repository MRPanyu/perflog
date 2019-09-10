package perflog;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 实际进行日志写入的类
 * 
 * @author panyu
 *
 */
class PerfLogWriter {

	private static Map<String, Map<String, PerfStepStatistics>> store = new LinkedHashMap<String, Map<String, PerfStepStatistics>>();

	static {
		startTimer();
	}

	/**
	 * 追加一条性能日志记录
	 * 
	 * @param rootStep 一次性能日志记录的根步骤
	 */
	public synchronized static void append(PerfStep rootStep) {
		String logName = rootStep.getLogName();
		Map<String, PerfStepStatistics> map = store.get(logName);
		if (map == null) {
			map = new LinkedHashMap<String, PerfStepStatistics>();
			store.put(logName, map);
		}
		appendRec(rootStep, map);
	}

	private static void appendRec(PerfStep step, Map<String, PerfStepStatistics> map) {
		String logName = step.getLogName();
		String stepName = step.getStepName();
		PerfStepStatistics st = map.get(stepName);
		if (st == null) {
			st = new PerfStepStatistics(logName, stepName);
			map.put(stepName, st);
		}
		st.mergeStep(step);
		for (PerfStep subStep : step.getSubSteps()) {
			appendRec(subStep, map);
		}
	}

	private synchronized static void writeLog() {
		for (Map.Entry<String, Map<String, PerfStepStatistics>> entry : store.entrySet()) {
			String logName = entry.getKey();
			Map<String, PerfStepStatistics> map = entry.getValue();
			Log logger = LogFactory.getLog(logName);
			StringBuilder sb = new StringBuilder();
			sb.append(
					"LogName\tStepName\tExecuteCount\tTotalUseTime\tTotalUseTimeExceptSubSteps\tMaxUseTime\tMaxUseTimeExceptSubSteps\n");
			for (Map.Entry<String, PerfStepStatistics> stepEntry : map.entrySet()) {
				PerfStepStatistics st = stepEntry.getValue();
				sb.append(logName).append("\t").append(st.getStepName()).append("\t");
				sb.append(st.getExecuteCount()).append("\t");
				sb.append(st.getTotalUseTime()).append("\t").append(st.getTotalUseTimeExceptSubSteps()).append("\t");
				sb.append(st.getMaxUseTime()).append("\t").append(st.getMaxUseTimeExceptSubSteps()).append("\n");
			}
			logger.info(sb);
		}
	}

	private static void startTimer() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.add(Calendar.MINUTE, 1);
		Timer timer = new Timer("PerfLogWriter", true);
		timer.schedule(new TimerTask() {
			public void run() {
				writeLog();
			}
		}, cal.getTime(), 60000);
	}

}
