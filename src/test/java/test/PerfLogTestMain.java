package test;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import perflog.PerfLog;

public class PerfLogTestMain {

	private static Log logger = LogFactory.getLog(PerfLogTestMain.class);

	public static void main(String[] args) throws Exception {
		test();
	}

	public static void test() throws Exception {
		for (int i = 0; i < 300; i++) {
			logger.info("Loop " + i);
			PerfLog.startLog("mainMethod"); // 开始记录性能日志
			PerfLog.startStep("mainMethod"); // 记录最外层步骤开始
			try {
				Thread.sleep(200);

				PerfLog.startStep("subStep1"); // 记录子步骤1开始
				try {
					Thread.sleep(50); // 实际子步骤1的代码
					PerfLog.startStep("subStep11");
					try {
						Thread.sleep(50);
					} finally {
						PerfLog.endStep("subStep11");
					}
					Thread.sleep(50);
				} finally {
					PerfLog.endStep("subStep1"); // 记录子步骤1结束
				}

				Thread.sleep(200);

				PerfLog.startStep("subStep2"); // 记录子步骤2开始
				try {
					Thread.sleep(100); // 实际子步骤2的代码
				} finally {
					PerfLog.endStep("subStep2"); // 记录子步骤2结束
				}

				Thread.sleep(200);

			} finally {
				PerfLog.endStep("mainMethod"); // 记录最外层步骤结束
				PerfLog.endLog("mainMethod"); // 结束记录性能日志
			}
		}
	}

}
