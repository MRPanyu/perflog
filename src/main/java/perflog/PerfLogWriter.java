package perflog;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * 实际进行日志写入的类
 * 
 * @author panyu
 *
 */
class PerfLogWriter {

	private static Map<String, Map<String, PerfStepStatistics>> statisticsStore = new LinkedHashMap<String, Map<String, PerfStepStatistics>>();
	private static Map<String, Set<PerfStep>> top3Store = new LinkedHashMap<String, Set<PerfStep>>();

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
		// 统计数据记录
		Map<String, PerfStepStatistics> map = statisticsStore.get(logName);
		if (map == null) {
			map = new LinkedHashMap<String, PerfStepStatistics>();
			statisticsStore.put(logName, map);
		}
		appendStatisticsRec(rootStep, map);
		// 耗时前三记录
		Set<PerfStep> set = top3Store.get(logName);
		if (set == null) {
			set = new TreeSet<PerfStep>(new Comparator<PerfStep>() {
				@Override
				public int compare(PerfStep o1, PerfStep o2) {
					long u1 = o1.getUseTime();
					long u2 = o2.getUseTime();
					if (u1 < u2) {
						return -1;
					} else if (u1 > u2) {
						return 1;
					} else {
						return 0;
					}
				}
			});
			top3Store.put(logName, set);
		}
		set.add(rootStep);
		if (set.size() > 3) { // 如果个数大于3，则去掉最小的一个
			Iterator<PerfStep> iter = set.iterator();
			iter.next();
			iter.remove();
		}
	}

	private static void appendStatisticsRec(PerfStep step, Map<String, PerfStepStatistics> map) {
		String logName = step.getLogName();
		String stepName = step.getStepName();
		PerfStepStatistics st = map.get(stepName);
		if (st == null) {
			st = new PerfStepStatistics(logName, stepName);
			map.put(stepName, st);
		}
		st.mergeStep(step);
		for (PerfStep subStep : step.getSubSteps()) {
			appendStatisticsRec(subStep, map);
		}
	}

	private static void writeLog() {
		// 同步锁并快速交换记录存储对象，减少写日志对记录方法的性能影响
		Map<String, Map<String, PerfStepStatistics>> oldStatisticsStore = null;
		Map<String, Set<PerfStep>> oldTop3Store = null;
		synchronized (PerfLogWriter.class) {
			oldStatisticsStore = statisticsStore;
			statisticsStore = new LinkedHashMap<String, Map<String, PerfStepStatistics>>();
			oldTop3Store = top3Store;
			top3Store = new LinkedHashMap<String, Set<PerfStep>>();
		}
		// 统计信息记录
		String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
		for (Map.Entry<String, Map<String, PerfStepStatistics>> entry : oldStatisticsStore.entrySet()) {
			String logName = entry.getKey();
			Map<String, PerfStepStatistics> map = entry.getValue();
			Log logger = LogFactory.getLog("perflog.statistics." + logName);
			StringBuilder sb = new StringBuilder();
			for (Map.Entry<String, PerfStepStatistics> stepEntry : map.entrySet()) {
				PerfStepStatistics st = stepEntry.getValue();
				sb.append(timeStr).append("\t");
				sb.append(logName).append("\t").append(st.getStepName()).append("\t");
				sb.append(st.getExecuteCount()).append("\t");
				sb.append(st.getTotalUseTime()).append("\t").append(st.getTotalUseTimeExceptSubSteps()).append("\t");
				sb.append(st.getMaxUseTime()).append("\t").append(st.getMaxUseTimeExceptSubSteps()).append("\n");
			}
			logger.info(sb);
		}
		// 耗时前三信息记录
		for (Map.Entry<String, Set<PerfStep>> entry : oldTop3Store.entrySet()) {
			String logName = entry.getKey();
			Set<PerfStep> set = entry.getValue();
			Log logger = LogFactory.getLog("perflog.top3." + logName);
			StringBuilder sb = new StringBuilder();
			for (PerfStep step : set) {
				sb.append("<").append(timeStr).append("> <").append(logName).append("> --------------------\n");
				writeStepRec(step, sb, 1, step.getStartTime());
			}
			logger.info(sb);
		}
	}

	private static long writeStepRec(PerfStep currentStep, StringBuilder sb, int level, long lastTimestamp) {
		SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		if (currentStep.getStartTime() > lastTimestamp) {
			indent(sb, level);
			sb.append("(code in ").append(currentStep.getParentStep().getStepName()).append(") use=")
					.append(currentStep.getStartTime() - lastTimestamp).append("\n");
		}
		lastTimestamp = currentStep.getStartTime();
		indent(sb, level);
		sb.append("[").append(currentStep.getStepName()).append("] ");
		sb.append("use=").append(currentStep.getUseTime()).append(", useEx=")
				.append(currentStep.getUseTimeExcludeSubSteps());
		sb.append(", start=").append(fmt.format(currentStep.getStartTime())).append(", end=")
				.append(fmt.format(currentStep.getEndTime())).append("\n");
		for (PerfStep subStep : currentStep.getSubSteps()) {
			lastTimestamp = writeStepRec(subStep, sb, level + 1, lastTimestamp);
		}
		if (currentStep.getEndTime() > lastTimestamp) {
			indent(sb, level + 1);
			sb.append("(code in ").append(currentStep.getStepName()).append(") use=")
					.append(currentStep.getEndTime() - lastTimestamp).append("\n");
		}
		lastTimestamp = currentStep.getEndTime();
		return lastTimestamp;
	}

	private static void indent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append("  ");
		}
	}

	private static void startTimer() {
		Calendar cal = Calendar.getInstance();
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.add(Calendar.MINUTE, 1);
		Timer timer = new Timer("PerfLogWriter", true);
		timer.schedule(new TimerTask() {
			public void run() {
				writeLog();
			}
		}, cal.getTime(), 60000);
	}

}
