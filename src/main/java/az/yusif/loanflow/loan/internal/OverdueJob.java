package az.yusif.loanflow.loan.internal;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

import java.time.Clock;
import java.time.LocalDate;

@DisallowConcurrentExecution
public class OverdueJob extends QuartzJobBean {

    private final OverdueProcessor processor;
    private final Clock clock;

    public OverdueJob(OverdueProcessor processor, Clock clock) {
        this.processor = processor;
        this.clock = clock;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) {
        processor.process(LocalDate.now(clock));
    }
}
