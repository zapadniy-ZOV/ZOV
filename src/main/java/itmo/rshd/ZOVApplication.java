package itmo.rshd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZOVApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZOVApplication.class, args);
	}

}