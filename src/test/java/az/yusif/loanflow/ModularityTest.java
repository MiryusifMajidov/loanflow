package az.yusif.loanflow;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(LoanflowApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void expectedModulesExist() {
        assertThat(modules.stream().map(ApplicationModule::getName))
                .containsExactlyInAnyOrder("application", "scoring", "loan", "notification", "shared");
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
