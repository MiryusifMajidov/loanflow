package az.yusif.loanflow.loan.internal;

import az.yusif.loanflow.shared.SharedConfig;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class QuartzConfig {

    @Bean
    JobDetail overdueJobDetail() {
        return JobBuilder.newJob(OverdueJob.class)
                .withIdentity("overdue-installments", "loans")
                .withDescription("Marks overdue installments and accrues penalties")
                .storeDurably()
                .build();
    }

    @Bean
    Trigger overdueJobTrigger(JobDetail overdueJobDetail, LoanProperties properties) {
        return TriggerBuilder.newTrigger()
                .forJob(overdueJobDetail)
                .withIdentity("overdue-installments-daily", "loans")
                .withSchedule(CronScheduleBuilder.cronSchedule(properties.overdueCron())
                        .inTimeZone(TimeZone.getTimeZone(SharedConfig.BAKU))
                        .withMisfireHandlingInstructionFireAndProceed())
                .build();
    }
}
