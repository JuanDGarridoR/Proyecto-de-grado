package com.proyectogrado.persona_mayor_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Solo la réplica con tareas.programadas.activas corre las tareas
 * programadas; la otra no las agenda.
 */
class TareasProgramadasConfigTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(TareasProgramadasConfig.class);

    @Test
    void porDefectoLasTareasProgramadasEstanActivas() {
        contexto.run(app -> assertThat(app).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class));
    }

    @Test
    void laReplicaQueLasApagaNoAgendaNinguna() {
        contexto.withPropertyValues("tareas.programadas.activas=false")
                .run(app -> assertThat(app).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class));
    }
}
