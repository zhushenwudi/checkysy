package com.ilab.checkysy.database;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Generated;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Property;

@Entity
public class DayKeyToListValue {

    @Id(autoincrement = true)
    private Long id;

    @Property(nameInDb = "currentDay")
    private String day;

    @Property(nameInDb = "listJson")
    private String list;

    @Generated(hash = 1791629292)
    public DayKeyToListValue(Long id, String day, String list) {
        this.id = id;
        this.day = day;
        this.list = list;
    }

    @Generated(hash = 873954762)
    public DayKeyToListValue() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDay() {
        return this.day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getList() {
        return this.list;
    }

    public void setList(String list) {
        this.list = list;
    }
}
