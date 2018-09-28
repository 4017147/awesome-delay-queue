/*
 * Copyright (c) 2018
 * User:qinghua.xu
 * File:AbstractAwesomeExecutor.java
 * Date:2018/09/28
 */

package me.xqh.awesome.delayqueue.scheduling;

import me.xqh.awesome.delayqueue.storage.api.StorageFactory;
import me.xqh.awesome.delayqueue.storage.api.StorageService;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * @author qinghua.xu
 * @date 2018/9/28
 **/
public abstract class AbstractAwesomeExecutor implements AwesomeExecutor {
    protected StorageService storageService;
    public AbstractAwesomeExecutor(){
        init();
    }

    private void init(){
        ServiceLoader<StorageFactory> sl = ServiceLoader.load(StorageFactory.class);
        Iterator<StorageFactory> iter = sl.iterator();
        while (iter.hasNext()){
            StorageFactory storageFactory = iter.next();
            storageService = storageFactory.getStorageService();
            break;
        }
    }


}
