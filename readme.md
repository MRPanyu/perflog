# PerfLog简易性能日志组件

参考了一些现代性能监控软件的理念做的一个简易性能日志输出组件，既可以结合aop工具（如aspectj）来自动拦截方法级别的耗时，也可以手工在程序中添加代码。这样性能监控粒度的粗细可以比较自由地控制。

## 1. 程序设计

### 1.1 PerfLog

整个组件包中，只有PerfLog类是public的，供程序中添加性能日志记录，其中只包含四个方法：

- **startLog/endLog**: 整个性能日志记录的总开关。某个执行线程中，只有startLog到endLog中间的startStep/endStep，才会被作为有效的性能日志记录下来。有这么一个总开关的好处在于，当你只想分析某段具体程序的性能，但也需要在某处比较公用的代码段进行startStep/endStep，startLog/endLog方法的存在就能帮你过滤掉这些公共代码与你具体想分析的代码段无关的调用。另外startLog/endLog带一个logName参数，可以用于区分不同的流程（既“你想要分析的某段具体程序”），不同logName下的步骤，即使步骤名称相同，也会作为不同的实例分别记录。
- **startStep/endStep**: 用于标记程序流程中的一个具体步骤开始和结束。建议以try...finally方式调用。

### 1.2 PerfStep

某个程序步骤的数据记录，包含起始终止时间点，耗时等等信息。

因为程序有相互调用的关系，步骤本身是可以包含子步骤的，子步骤下还可以继续包含子步骤。

步骤中useTimeExceptSubSteps属性，表示排除掉所有包含的子步骤时间后余下来的耗时。

### 1.3 PerfStatistics

某个具体步骤多次调用的统计信息，包含调用次数、总耗时等等信息。

### 1.4 PerfLogWriter

PerfLog类在执行endLog时，会调用这个类进行实际的日志记录。目前的实现是将每次endLog后的所有步骤计入统计信息，然后每分钟输出到日志文件（使用的是apache-commons-logging）中。

## 2. 添加性能日志的方式

### 2.1 程序中直接添加性能日志记录

直接在现有程序中，增加PerfLog类的调用即可，示例的程序：

```java
public static void mainMethod() {
    PerfLog.startLog("mainMethod"); // 开始记录性能日志
    PerfLog.startStep("mainMethod"); // 记录最外层步骤开始
    try {
        PerfLog.startStep("subStep1"); // 记录子步骤1开始
        try {
            subStep1(); // 实际子步骤1的代码
        } finally {
            PerfLog.endStep("subStep1"); // 记录子步骤1结束
        }

        PerfLog.startStep("subStep2"); // 记录子步骤2开始
        try {
            subStep2(); // 实际子步骤2的代码
        } finally {
            PerfLog.endStep("subStep2"); // 记录子步骤2结束
        }

    } finally {
        PerfLog.endStep("mainMethod"); // 记录最外层步骤结束
        PerfLog.endLog("mainMethod"); // 结束记录性能日志
    }
}
```

### 2.2 使用aspectj进行aop性能日志记录

可以使用aspectj进行aop方式，对方法层切面注入性能日志代码。

#### 2.2.1 编写切面程序

示例的切面程序：

aspects/PerfLogAspect.aj

```java
package perflog;

import perflog.PerfLog;

aspect PerfLogAspect {

    /** 需要当作性能日志开始/结束的方法切点 */
    pointcut perfLog(): execution(* yourpackage.MainClass.mainMethod(..))
                    || execution(* otherpackage.OtherClass.otherMethod(..));

    /** 需要当作步骤记录的方法切点 */
    pointcut perfStep(): !perfLog()
                    && (execution(* yourpackage.*..*(..)
                        || execution(* otherpackage.*..*(..));

    before() : perfLog() {
        String logName = thisJoinPoint.getSignature().toShortString();
        PerfLog.startLog(logName);
        PerfLog.startStep(logName);
    }

    after() : perfLog() {
        String logName = thisJoinPoint.getSignature().toShortString();
        PerfLog.endStep(logName);
        PerfLog.endLog(logName);
    }

    before() : perfStep() {
        String stepName = thisJoinPoint.getSignature().toShortString();
        PerfLog.startStep(stepName);
    }

    after() : perfStep() {
        String stepName = thisJoinPoint.getSignature().toShortString();
        PerfLog.endStep(stepName);
    }

}
```

#### 2.2.2 进行切面编译

因为程序中spring aop一般也会使用aspectj进行动态aop生成，性能日志的记录，建议使用aspectj静态编译方式，这样既不会与现有程序冲突，也可以覆盖到非spring bean的程序。

这里只简单介绍一下aspectj静态编译的使用方法，具体请参考aspectj文档：

##### 命令行编译方式

1. 下载一个aspectj完整包（是一个jar包，如aspectj-1.5.2a.jar），版本请与程序中aspectjweaver.jar的版本一致。
2. 下载下来的实际是个安装包，可以用 `java -jar aspectj-1.5.2a.jar` 来执行。安装过程中注意选择正确版本的JDK目录，如你需要用JDK6来正常编译程序的话，就要选择JDK6的目录。
3. 使用ajc命令进行编译（注意如果编译程序的JDK与你系统默认JDK不一致，还要设置下JAVA_HOME避免冲突）：

```cmd
set JAVA_HOME=C:\Program Files\Java\jdk1.6.0_45

ajc -inpath classes -sourceroots aspects -classpath "lib/aspectjrt-1.5.2a.jar;lib/perflog-1.0.0.jar" -outjar out.jar
```

参数说明：

- inpath: 预先编译好的原程序class文件
- sourceroots: 切面文件的根目录（下面放.aj文件，也要按java包结构目录层级）
- classpath: 编译时的classpath，注意至少需要aspectjrt.jar和perflog.jar
- outjar: 输出含切面内容的文件到具体的jar包名称

之后整理下文件，使用编译后含切面程序的class文件替换原来的class文件，发布到具体环境上。

##### ant编译方式

1. 下载一个aspectj完整包，可以按命令行编译方式那样安装，也可以直接解压，然后lib目录下找到一个aspectjtools.jar文件，复制到 $ANT_HOME/lib 目录下
2. 执行ant命令前注意修改一下JAVA_HOME环境变量到具体编译需要的JDK版本。
3. 修改ant配置文件，在javac编译后，再增加一个iac进行切面编译/注入

下面示例文件对应的工程结构：

- src/java: 存放java源代码
- src/aspects: 存放切面源文件（.aj文件）
- lib: 存放jar包
- build/classes_temp: 输出原始class文件
- build/classes: 输出切面编译后的class文件

```xml
<project name="demo" default="build" basedir=".">
    <!-- 声明iajc的task -->
    <taskdef resource="org/aspectj/tools/ant/taskdefs/aspectjTaskdefs.properties">
        <classpath>
            <pathelement location="aspectjtools.jar"/>
        </classpath>
    </taskdef>

    <target name="build">
        <mkdir dir="build" />
        <mkdir dir="build/classes_temp"/>
        <mkdir dir="build/classes" />
        <!-- 先进行javac正常编译程序 -->
        <javac srcdir="src/java" destdir="build/classes_temp"
            encoding="UTF-8" source="1.6" target="1.6" fork="true" nowarn="true" debug="true" debuglevel="lines,vars,source" includeantruntime="true">
            <classpath>
                <fileset dir="lib">
                    <include name="**/*.jar"/>
                </fileset>
            </classpath>
        </javac>
        <!-- 再使用iajc进行切面编译 -->
        <iajc sourceRoots="src/aspects" inpath="build/classes_temp" destDir="build/classes" source="1.6"  debug="true" debuglevel="lines,vars,source" >
            <classpath>
                <fileset dir="lib">
                    <include name="**/*.jar"/>
                </fileset>
            </classpath>
        </iajc>
    </target>
</project>
```

## 3. 性能日志的输出及分析

### 3.1 日志输出配置

PerfLogWriter目前以apache-commons-logging日志组件进行性能日志输出，输出的logger名称有以下两种：

- perflog.statistics.<日志名称>: （日志名称即 `PerfLog.startLog` 方法传入的参数值。）这个日志记录统计信息。
- perflog.top3.<日志名称>: 这个日志记录每分钟每个日志名称下耗时前三的具体调用时序情况。

以最基础的log4j.properties配置文件为例，一般建议的配置方式：

```ini
# 输出其他系统日志的appender，这里用一个ConsoleAppender代表
log4j.appender.CONSOLE=org.apache.log4j.ConsoleAppender
log4j.appender.CONSOLE.Target=System.out
log4j.appender.CONSOLE.layout=org.apache.log4j.PatternLayout
log4j.appender.CONSOLE.layout.ConversionPattern=[%-5p][%10d][%x][%c] - %m%n

# 输出性能日志统计数据到具体文件，注意输出格式就是直接%m不需要任何额外内容
log4j.appender.PERF_STAT=org.apache.log4j.DailyRollingFileAppender
log4j.appender.PERF_STAT.File=perf_statistics.log
log4j.appender.PERF_STAT.layout=org.apache.log4j.PatternLayout
log4j.appender.PERF_STAT.layout.ConversionPattern=%m

# 输出性能日志耗时前三明细数据到具体文件，注意输出格式就是直接%m不需要任何额外内容
log4j.appender.PERF_TOP3=org.apache.log4j.DailyRollingFileAppender
log4j.appender.PERF_TOP3.File=perf_top3.log
log4j.appender.PERF_TOP3.layout=org.apache.log4j.PatternLayout
log4j.appender.PERF_TOP3.layout.ConversionPattern=%m

# 其他日志类别
log4j.rootLogger=INFO,CONSOLE

# 性能日志统计数据的日志类别配置
log4j.logger.perflog.statistics=INFO,PERF_STAT
log4j.additivity.perflog.statistics=false

# 性能日志明细数据的日志类别配置
log4j.logger.perflog.top3=INFO,PERF_TOP3
log4j.additivity.perflog.top3=false
```

### 3.2 日志分析

#### 3.2.1 统计数据日志分析

以下是一个统计数据日志输出的示例：

```txt
2019-09-16 15:05:00	mainMethod	mainMethod	5	4274	3008	855	603
2019-09-16 15:05:00	mainMethod	subStep1	5	761	508	153	102
2019-09-16 15:05:00	mainMethod	subStep11	5	253	253	51	51
2019-09-16 15:05:00	mainMethod	subStep2	5	505	505	101	101
2019-09-16 15:06:00	mainMethod	mainMethod	70	59823	42107	856	603
2019-09-16 15:06:00	mainMethod	subStep1	70	10667	7110	153	102
2019-09-16 15:06:00	mainMethod	subStep11	70	3557	3557	51	51
2019-09-16 15:06:00	mainMethod	subStep2	70	7049	7049	101	101
```

统计数据日志输出的实际是一个tab分隔的多列文件（TSV文件）。每一行是一个步骤的统计数据。几列内容分别是：

- 日志输出时间: 一般来说是每分钟输出一次统计数据，除非当时根本没有任何相关程序访问。
- 日志名称: `PerfLog.startLog`方法的入参
- 步骤名称: `PerfLog.startStep`方法的入参
- 调用次数: 该步骤在这一分钟内总共被调用了几次
- 总耗时: 该步骤在这一分钟内所有调用中，总共执行了多少毫秒
- 总耗时（排除子步骤）: 排除掉子步骤执行时间后的总耗时，即子步骤没有包含到的部分耗时多久
- 最大耗时: 耗时时间最长的一次的值
- 最大耗时（排除子步骤）: 排除子步骤耗时时间最长的一次的值

一般来说，可以使用工具将内容直接导入到数据库临时创建的表中，方便排序/查询。

示例建表sql（oracle的，应该很容易转成其他库的建表sql吧）：

```sql
create table perf_statistics (
  logTime date,
  logName varchar2(200),
  stepName varchar2(200),
  executeCount number(20),
  totalUseTime number(20),
  totalUseTimeEx number(20),
  maxUseTime number(20),
  maxUseTimeEx number(20)
);
```

如果你的文本工具有正则表达式替换功能的话，可以这样将日志输出内容替换成对应的insert语句（当然如果不是oracle库的话可能需要调整下to_date函数）：

查找：

```regexp
^(.+)\t(.+)\t(.+)\t(.+)\t(.+)\t(.+)\t(.+)\t(.+)$
```

替换：

```regexp
insert into perf_statistics (logTime, logName, stepName, executeCount, totalUseTime, totalUseTimeEx, maxUseTime, maxUseTimeEx) values (to_date('$1', 'yyyy-mm-dd hh24:mi:ss'), '$2', '$3', $4, $5, $6, $7, $8);
```

#### 3.2.2 明细日志分析

以下是一个明细日志输出的示例：

```txt
<2019-09-16 15:18:00> <mainMethod> --------------------
  [mainMethod] use=854, useEx=600, start=2019-09-16 15:17:00.204, end=2019-09-16 15:17:01.058
    (code in mainMethod) use=200
    [subStep1] use=153, useEx=102, start=2019-09-16 15:17:00.404, end=2019-09-16 15:17:00.557
      (code in subStep1) use=51
      [subStep11] use=51, useEx=51, start=2019-09-16 15:17:00.455, end=2019-09-16 15:17:00.506
        (code in subStep11) use=51
      (code in subStep1) use=51
    (code in mainMethod) use=200
    [subStep2] use=101, useEx=101, start=2019-09-16 15:17:00.757, end=2019-09-16 15:17:00.858
      (code in subStep2) use=101
    (code in mainMethod) use=200
<2019-09-16 15:18:00> <mainMethod> --------------------
  [mainMethod] use=855, useEx=601, start=2019-09-16 15:16:59.349, end=2019-09-16 15:17:00.204
    (code in mainMethod) use=200
    [subStep1] use=153, useEx=102, start=2019-09-16 15:16:59.549, end=2019-09-16 15:16:59.702
      (code in subStep1) use=51
      [subStep11] use=51, useEx=51, start=2019-09-16 15:16:59.600, end=2019-09-16 15:16:59.651
        (code in subStep11) use=51
      (code in subStep1) use=51
    (code in mainMethod) use=200
    [subStep2] use=101, useEx=101, start=2019-09-16 15:16:59.902, end=2019-09-16 15:17:00.003
      (code in subStep2) use=101
    (code in mainMethod) use=201
```

明细日志大致按拦截到的步骤的调用时序进行打印：

- 尖括号的行表示某个日志流程（PerfLog.startLog），打印日志输出时间和日志流程的名称。
- 方括号的行表示某个具体步骤。这些行前面会有每层两个空格的缩进，缩进的步骤表示是外层步骤的子步骤。打印内容包含步骤名称，耗时，排除子步骤的耗时，以及起始结束时间。
- 圆括号的行表示步骤内部（特别是外层步骤开始到某个子步骤之前或者两个子步骤之间的）分段的代码执行耗时。这个只有耗时超过1毫秒才会打印出来，缩进的层级与子步骤相同。主要用于分析还有哪些位置的代码可能需要额外增加步骤拦截点进行分析。这个会显示外层步骤名称，以及这段代码的耗时。
