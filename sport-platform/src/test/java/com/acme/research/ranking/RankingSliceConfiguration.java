package com.acme.research.ranking;

import java.util.Map;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

@Configuration(proxyBeanMethods = false)
class RankingSliceConfiguration {
    @Bean(destroyMethod="close")
    PostgreSQLContainer postgres() {
        PostgreSQLContainer pg = new PostgreSQLContainer("postgres:16");
        pg.start();
        return pg;
    }
    @Bean(destroyMethod="close")
    HikariDataSource pool(PostgreSQLContainer postgres) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        ds.setMaximumPoolSize(4);
        return ds;
    }
    @Bean @Primary DataSource dataSource(HikariDataSource pool) {
        DataSource ds = JdbcMetrics.wrap(pool);
        new ResourceDatabasePopulator(
                new ClassPathResource("ranking-experiment/schema.sql"),
                new ClassPathResource(
                        "db/migration/V027__protocol_assignment_categories.sql"
                )
        ).execute(ds);
        return ds;
    }
    @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try (var in = new ClassPathResource("ranking-experiment/parser.sql").getInputStream()) {
            jdbc.execute(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        return jdbc;
    }
    @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        var f = new LocalContainerEntityManagerFactoryBean();
        f.setDataSource(dataSource);
        f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        f.setManagedTypes(PersistenceManagedTypes.of(
                ResultEntity.class.getName(), CompetitionUnitEntity.class.getName(),
                CompetitionUnitEntryEntity.class.getName()));
        f.setJpaPropertyMap(Map.of(
                "hibernate.hbm2ddl.auto", "validate",
                "hibernate.jdbc.batch_size", Integer.getInteger("ranking.batchSize", 0),
                "hibernate.order_updates", true,
                "hibernate.show_sql", false));
        return f;
    }
    @Bean EntityManager entityManager(EntityManagerFactory factory) {
        return SharedEntityManagerCreator.createSharedEntityManager(factory);
    }
    @Bean JpaTransactionManager transactionManager(EntityManagerFactory factory, DataSource dataSource) {
        var manager = new JpaTransactionManager(factory);
        manager.setDataSource(dataSource);
        return manager;
    }
    @Bean CompetitionUnitRepository unitRepository(EntityManager em) {
        return new JpaRepositoryFactory(em).getRepository(CompetitionUnitRepository.class);
    }
    @Bean CompetitionUnitEntryRepository entryRepository(EntityManager em) {
        return new JpaRepositoryFactory(em).getRepository(CompetitionUnitEntryRepository.class);
    }
    @Bean ResultRepository resultRepository(EntityManager em) {
        return new JpaRepositoryFactory(em).getRepository(ResultRepository.class);
    }
    @Bean RankingExperiment experiment(CompetitionUnitRepository units,
            CompetitionUnitEntryRepository entries, ResultRepository results,
            EntityManager em, JdbcTemplate jdbc, JpaTransactionManager manager) {
        return new RankingExperiment(units, entries, results, em, jdbc, manager);
    }
    @Bean RankingFixtures fixtures(JdbcTemplate jdbc) { return new RankingFixtures(jdbc); }
}
