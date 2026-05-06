package id.ac.ui.cs.advprog.yomu.forum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {
    "id.ac.ui.cs.advprog.yomu.forum",
    "id.ac.ui.cs.advprog.yomu.shared" 
})
public class ForumApplication {
    public static void main(String[] args) {
        SpringApplication.run(ForumApplication.class, args);
    }
}