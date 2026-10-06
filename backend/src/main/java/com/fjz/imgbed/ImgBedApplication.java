package com.fjz.imgbed;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
@MapperScan("com.fjz.imgbed.**.mapper")
public class ImgBedApplication {

    public static void main(String[] args) throws Exception {
        // SQLite 驱动不会自动创建目录，先确保数据目录存在
        Files.createDirectories(Path.of("data"));
        SpringApplication.run(ImgBedApplication.class, args);
    }
}
