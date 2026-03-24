package dev.harrjdk.robotmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RobotMcpApplication {

    public static void main(String[] args) {
        // Must be set before AWT initializes — spring.main.headless=false is applied too late
        System.setProperty("java.awt.headless", "false");
        SpringApplication.run(RobotMcpApplication.class, args);
    }

}
