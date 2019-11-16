package com.ilab.checkysy.database;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;

@Entity
public class SkipEnty {
    @Id(autoincrement = true)
    private Long id;

    @Property(nameInDb = "skipFile")
    private String fileName;

    @Generated(hash = 1985459417)
    public SkipEnty(Long id, String fileName) {
        this.id = id;
        this.fileName = fileName;
    }

    @Generated(hash = 1339070904)
    public SkipEnty() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFileName() {
        return this.fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
}
