package com.example.phoenix;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication(exclude = {
		// This stops Spring from trying to auto-wire its own default Weaviate store
		// org.springframework.ai.autoconfigure.vectorstore.weaviate.WeaviateVectorStoreAutoConfiguration.class
})
public class PhoenixServiceApplication {

	private static final Logger log = LoggerFactory.getLogger(PhoenixServiceApplication.class);

	public static void main(String[] args) {
		log.info("Starting PhoenixService application");
		var ctx = SpringApplication.run(PhoenixServiceApplication.class, args);
		log.info("PhoenixService started with profiles: {}", String.join(",", ctx.getEnvironment().getActiveProfiles()));
	}

}
