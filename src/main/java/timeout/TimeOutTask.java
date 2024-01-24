package timeout;

import java.util.concurrent.*;


public abstract class TimeOutTask {
    private ExecutorService executorService= Executors.newFixedThreadPool(1);

    public void run(int timeoutSeconds) {
        Future<String> future = executorService.submit(new Callable<String>() {
            @Override
            public String call() {
                task();
                return  "OK";
            }
        });
        try {
            String result = future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException e) {
            future.cancel(true);
            System.out.println("TimeOutTask: TimeOut");
            timeoutHandler();
        } catch (ExecutionException e){
            e.printStackTrace();
        }
        executorService.shutdown();
    }

    protected abstract void task();

    protected void timeoutHandler(){};

}

class Demo{
    public static void main(String[] args) {
        new TimeOutTask() {
            @Override
            protected void task() {
                try {
                    TimeUnit.SECONDS.sleep(10);
                    System.out.println("finish");
                } catch (InterruptedException e) {
                    System.out.println("任务被中断");
                }
            }

            @Override
            protected void timeoutHandler(){
                System.out.println("回收资源");
            }
        }.run(5);
    }
}

