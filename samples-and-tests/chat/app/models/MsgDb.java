package models;

import javax.persistence.Entity;
import javax.persistence.Version;

import play.db.jpa.Model;

@Entity
public class MsgDb extends Model{
	private String name;
	private Integer age;
	
	@Version
	private Integer ver;
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setAge(Integer age) {
		this.age = age;
	}

	public Integer getAge() {
		return age;
	}

	public void setVer(Integer ver) {
		this.ver = ver;
	}

	public Integer getVer() {
		return ver;
	}
	
}
