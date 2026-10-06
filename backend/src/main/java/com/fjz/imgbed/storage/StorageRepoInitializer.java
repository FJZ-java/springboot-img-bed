package com.fjz.imgbed.storage;

import com.fjz.imgbed.storage.service.StorageRepoService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 首次启动播种：数据库里没有任何仓库配置时，把 application.yml 的默认仓库导入进来。
 * 这样老版本升级上来也是无缝的，不需要手动再配一遍。
 */
@Component
@Order(20)
@RequiredArgsConstructor
public class StorageRepoInitializer implements ApplicationRunner {

    private final StorageRepoService repoService;

    @Override
    public void run(ApplicationArguments args) {
        repoService.seedDefaultIfEmpty();
    }
}
