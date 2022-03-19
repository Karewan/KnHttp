package ovh.karewan.knhttp.core;

import java.util.concurrent.Executor;

public interface ExecutorSupplier {

	ANExecutor forNetworkTasks();

	ANExecutor forImmediateNetworkTasks();

	Executor forMainThreadTasks();
}
