package org.apache.commons.beanutils.test;

import java.lang.reflect.InvocationTargetException;
import org.apache.commons.beanutils.BeanUtilsBeanImpl;
import org.apache.commons.beanutils.ConvertUtilsBean;
import org.junit.Assert;
import org.junit.Test;

public class BeanUtilsBeanImplTest {
	public static class testClass {
		private String name;

		private int age;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getAge() {
			return age;
		}

		public void setAge(int age) {
			this.age = age;
		}

	}

	public static record testRecord(String name, int age) {

	}

	@Test
	public void copyProperties() throws IllegalAccessException, InvocationTargetException {
		testRecord orig = new testRecord("tom", 1);
		testClass dest = new testClass();
		new BeanUtilsBeanImpl(new ConvertUtilsBean() {
		}).copyProperties(dest, orig);
		Assert.assertEquals(dest.name, orig.name);
		Assert.assertEquals(dest.age, orig.age);
	}

}
