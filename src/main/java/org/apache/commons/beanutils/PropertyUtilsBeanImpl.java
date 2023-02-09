/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.commons.beanutils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.beanutils.expression.Resolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.deking.util.RecordReflectUtils;
import org.deking.util.common.RecComponent;

/**
 * Utility methods for using Java Reflection APIs to facilitate generic property
 * getter and setter operations on Java objects. Much of this code was
 * originally included in <code>BeanUtils</code>, but has been separated because
 * of the volume of code involved.
 * <p>
 * In general, the objects that are examined and modified using these methods
 * are expected to conform to the property getter and setter method naming
 * conventions described in the JavaBeans Specification (Version 1.0.1). No data
 * type conversions are performed, and there are no usage of any
 * <code>PropertyEditor</code> classes that have been registered, although a
 * convenient way to access the registered classes themselves is included.
 * <p>
 * For the purposes of this class, five formats for referencing a particular
 * property value of a bean are defined, with the <i>default</i> layout of an
 * identifying String in parentheses. However the notation for these formats and
 * how they are resolved is now (since BeanUtils 1.8.0) controlled by the
 * configured {@link Resolver} implementation:
 * <ul>
 * <li><strong>Simple (<code>name</code>)</strong> - The specified
 * <code>name</code> identifies an individual property of a particular JavaBean.
 * The name of the actual getter or setter method to be used is determined using
 * standard JavaBeans instrospection, so that (unless overridden by a
 * <code>BeanInfo</code> class, a property named "xyz" will have a getter method
 * named <code>getXyz()</code> or (for boolean properties only)
 * <code>isXyz()</code>, and a setter method named <code>setXyz()</code>.</li>
 * <li><strong>Nested (<code>name1.name2.name3</code>)</strong> The first name
 * element is used to select a property getter, as for simple references above.
 * The object returned for this property is then consulted, using the same
 * approach, for a property getter for a property named <code>name2</code>, and
 * so on. The property value that is ultimately retrieved or modified is the one
 * identified by the last name element.</li>
 * <li><strong>Indexed (<code>name[index]</code>)</strong> - The underlying
 * property value is assumed to be an array, or this JavaBean is assumed to have
 * indexed property getter and setter methods. The appropriate (zero-relative)
 * entry in the array is selected. <code>List</code> objects are now also
 * supported for read/write. You simply need to define a getter that returns the
 * <code>List</code></li>
 * <li><strong>Mapped (<code>name(key)</code>)</strong> - The JavaBean is
 * assumed to have an property getter and setter methods with an additional
 * attribute of type <code>java.lang.String</code>.</li>
 * <li><strong>Combined (<code>name1.name2[index].name3(key)</code>)</strong> -
 * Combining mapped, nested, and indexed references is also supported.</li>
 * </ul>
 *
 * @version $Id$
 * @see Resolver
 * @see PropertyUtils
 * @since 1.7
 */

public final class PropertyUtilsBeanImpl extends PropertyUtilsBean {
	/**
	 * Logging for this instance
	 */
	private final Log log = LogFactory.getLog(BeanUtils.class);

	/**
	 * <p>
	 * Copy property values from the "origin" bean to the "destination" bean for all
	 * cases where the property names are the same (even though the actual getter
	 * and setter methods might have been customized via <code>BeanInfo</code>
	 * classes). No conversions are performed on the actual property values -- it is
	 * assumed that the values retrieved from the origin bean are
	 * assignment-compatible with the types expected by the destination bean.
	 * </p>
	 *
	 * <p>
	 * If the origin "bean" is actually a <code>Map</code>, it is assumed to contain
	 * String-valued <strong>simple</strong> property names as the keys, pointing at
	 * the corresponding property values that will be set in the destination
	 * bean.<strong>Note</strong> that this method is intended to perform a "shallow
	 * copy" of the properties and so complex properties (for example, nested ones)
	 * will not be copied.
	 * </p>
	 *
	 * <p>
	 * Note, that this method will not copy a List to a List, or an Object[] to an
	 * Object[]. It's specifically for copying JavaBean properties.
	 * </p>
	 *
	 * @param dest Destination bean whose properties are modified
	 * @param orig Origin bean whose properties are retrieved
	 *
	 * @throws IllegalAccessException    if the caller does not have access to the
	 *                                   property accessor method
	 * @throws IllegalArgumentException  if the <code>dest</code> or
	 *                                   <code>orig</code> argument is null
	 * @throws InvocationTargetException if the property accessor method throws an
	 *                                   exception
	 * @throws NoSuchMethodException     if an accessor method for this propety
	 *                                   cannot be found
	 */
	public void copyProperties(final Object dest, final Object orig)
			throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

		if (dest == null) {
			throw new IllegalArgumentException("No destination bean specified");
		}
		if (orig == null) {
			throw new IllegalArgumentException("No origin bean specified");
		}

		if (orig instanceof DynaBean) {
			final DynaProperty[] origDescriptors = ((DynaBean) orig).getDynaClass().getDynaProperties();
			for (DynaProperty origDescriptor : origDescriptors) {
				final String name = origDescriptor.getName();
				if (isReadable(orig, name) && isWriteable(dest, name)) {
					try {
						final Object value = ((DynaBean) orig).get(name);
						if (dest instanceof DynaBean) {
							((DynaBean) dest).set(name, value);
						} else {
							setSimpleProperty(dest, name, value);
						}
					} catch (final NoSuchMethodException e) {
						if (log.isDebugEnabled()) {
							log.debug("Error writing to '" + name + "' on class '" + dest.getClass() + "'", e);
						}
					}
				}
			}
		} else if (orig instanceof Map) {
			final Iterator<?> entries = ((Map<?, ?>) orig).entrySet().iterator();
			while (entries.hasNext()) {
				final Map.Entry<?, ?> entry = (Entry<?, ?>) entries.next();
				final String name = (String) entry.getKey();
				if (isWriteable(dest, name)) {
					try {
						if (dest instanceof DynaBean) {
							((DynaBean) dest).set(name, entry.getValue());
						} else {
							setSimpleProperty(dest, name, entry.getValue());
						}
					} catch (final NoSuchMethodException e) {
						if (log.isDebugEnabled()) {
							log.debug("Error writing to '" + name + "' on class '" + dest.getClass() + "'", e);
						}
					}
				}
			}
		} else /* if (orig is a Record) */ {
			boolean isRecord = RecordReflectUtils.isRecord(orig.getClass());
			final Object[] origDescriptors = isRecord ? RecordReflectUtils.recordComponents(orig.getClass(), null)
					: getPropertyDescriptors(orig);
			for (Object origDescriptor : origDescriptors) {
//				@SuppressWarnings("preview")
//            	final String name = (switch (origDescriptor) {
//				case PropertyDescriptor p -> p.getName();
//				case RecordComponent r -> r.getName();
//				default -> throw new IllegalArgumentException("Unexpected value: " + origDescriptor);
//				});
				String name = null;
				if (origDescriptor instanceof PropertyDescriptor) {
					name = ((PropertyDescriptor) origDescriptor).getName();
				} else if (origDescriptor instanceof RecComponent) {
					name = ((RecComponent) origDescriptor).name();
				}
				if ((isReadable(orig, name) || isRecord) && isWriteable(dest, name)) {
					try {
						final Object value = isRecord
								? RecordReflectUtils.componentValue(orig,
										new RecComponent(name, ((RecComponent) origDescriptor).type(), -1))
								: getSimpleProperty(orig, name);
						if (dest instanceof DynaBean) {
							((DynaBean) dest).set(name, value);
						} else {
							setSimpleProperty(dest, name, value);
						}
					} catch (final NoSuchMethodException e) {
						if (log.isDebugEnabled()) {
							log.debug("Error writing to '" + name + "' on class '" + dest.getClass() + "'", e);
						}
					}
				}
			}
		}

	}

}
