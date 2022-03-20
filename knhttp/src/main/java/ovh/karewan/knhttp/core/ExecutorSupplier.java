package ovh.karewan.knhttp.core;

import java.util.concurrent.Executor;

public interface ExecutorSupplier {

	KnExecutor forNetworkTasks();

	KnExecutor forImmediateNetworkTasks();

	Executor forMainThreadTasks();
}
