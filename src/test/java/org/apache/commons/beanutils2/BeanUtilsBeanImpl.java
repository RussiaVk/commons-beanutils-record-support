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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.deking.util.RecordInvokeUtils;
import org.deking.util.RecordReflectUtils;
import org.deking.util.ReflectionUtils;
import org.deking.util.common.RecComponent;

/**
 * <p>
 * JavaBean property population methods.
 * </p>
 *
 * <p>
 * This class provides implementations for the utility methods in
 * {@link BeanUtils}. Different instances can be used to isolate caches between
 * classloaders and to vary the value converters registered.
 * </p>
 *
 * @version $Id$
 * @see BeanUtils
 * @since 1.7
 */

public final class BeanUtilsBeanImpl extends BeanUtilsBean {
	/**
	 * Logging for this instance
	 */
	private final Log log = LogFactory.getLog(BeanUtils.class);

	public BeanUtilsBeanImpl(final ConvertUtilsBean convertUtilsBean) {
		super(convertUtilsBean, new PropertyUtilsBeanImpl());
	}

	/**
	 * <p>
	 * Copy property values from the origin bean to the destination bean for all
	 * cases where the property names are the same. For each property, a conversion
	 * is attempted as necessary. All combinations of standard JavaBeans and
	 * DynaBeans as origin and destination are supported. Properties that exist in
	 * the origin bean, but do not exist in the destination bean (or are read-only
	 * in the destination bean) are silently ignored.
	 * </p>
	 *
	 * <p>
	 * If the origin "bean" is actually a <code>Map</code>, it is assumed to contain
	 * String-valued <strong>simple</strong> property names as the keys, pointing at
	 * the corresponding property values that will be converted (if necessary) and
	 * set in the destination bean. <strong>Note</strong> that this method is
	 * intended to perform a "shallow copy" of the properties and so complex
	 * properties (for example, nested ones) will not be copied.
	 * </p>
	 *
	 * <p>
	 * This method differs from <code>populate()</code>, which was primarily
	 * designed for populating JavaBeans from the map of request parameters
	 * retrieved on an HTTP request, is that no scalar->indexed or indexed->scalar
	 * manipulations are performed. If the origin property is indexed, the
	 * destination property must be also.
	 * </p>
	 *
	 * <p>
	 * If you know that no type conversions are required, the
	 * <code>copyProperties()</code> method in {@link PropertyUtils} will execute
	 * faster than this method.
	 * </p>
	 *
	 * <p>
	 * <strong>FIXME</strong> - Indexed and mapped properties that do not have
	 * getter and setter methods for the underlying array or Map are not copied by
	 * this method.
	 * </p>
	 *
	 * @param dest Destination bean whose properties are modified
	 * @param orig Origin bean whose properties are retrieved
	 *
	 * @throws IllegalAccessException    if the caller does not have access to the
	 *                                   property accessor method
	 * @throws IllegalArgumentException  if the <code>dest</code> or
	 *                                   <code>orig</code> argument is null or if
	 *                                   the <code>dest</code> property type is
	 *                                   different from the source type and the
	 *                                   relevant converter has not been registered.
	 * @throws InvocationTargetException if the property accessor method throws an
	 *                                   exception
	 */
	@Override
	public void copyProperties(final Object dest, final Object orig)
			throws IllegalAccessException, InvocationTargetException {

		// Validate existence of the specified beans
		if (dest == null) {
			throw new IllegalArgumentException("No destination bean specified");
		}
		if (orig == null) {
			throw new IllegalArgumentException("No origin bean specified");
		}
		if (log.isDebugEnabled()) {
			log.debug("BeanUtils.copyProperties(" + dest + ", " + orig + ")");
		}

		// Copy the properties, converting as necessary
		if (orig instanceof DynaBean) {
			final DynaProperty[] origDescriptors = ((DynaBean) orig).getDynaClass().getDynaProperties();
			for (DynaProperty origDescriptor : origDescriptors) {
				final String name = origDescriptor.getName();
				// Need to check isReadable() for WrapDynaBean
				// (see Jira issue# BEANUTILS-61)
				if (getPropertyUtils().isReadable(orig, name) && getPropertyUtils().isWriteable(dest, name)) {
					final Object value = ((DynaBean) orig).get(name);
					copyProperty(dest, name, value);
				}
			}
		} else if (orig instanceof Map) {
			@SuppressWarnings("unchecked")
			final
			// Map properties are always of type <String, Object>
			Map<String, Object> propMap = (Map<String, Object>) orig;
			for (final Map.Entry<String, Object> entry : propMap.entrySet()) {
				final String name = entry.getKey();
				if (getPropertyUtils().isWriteable(dest, name)) {
					copyProperty(dest, name, entry.getValue());
				}
			}
		} else /* if (orig is a standard JavaBean) */ {
			if(RecordInvokeUtils.isRecord(dest.getClass())) {
				throw new IllegalArgumentException("The destination can't be record type");
			}
			boolean isRecord = RecordInvokeUtils.isRecord(orig.getClass());
			final Object[] origDescriptors = isRecord ? RecordInvokeUtils.recordComponents(orig.getClass(), null)
					: getPropertyUtils().getPropertyDescriptors(orig);
			for (Object origDescriptor : origDescriptors) {
//				@SuppressWarnings("preview")
//				final String name = (switch (origDescriptor) {
//				case PropertyDescriptor p -> p.getName();
//				case java.lang.reflect.RecordComponent r -> r.getName();
//				default -> throw new IllegalArgumentException("Unexpected value: " + origDescriptor);
//				});
				String name = null;
				if (origDescriptor instanceof PropertyDescriptor) {
					name = ((PropertyDescriptor) origDescriptor).getName();
				} else if (origDescriptor instanceof RecComponent) {
					name = ((RecComponent) origDescriptor).name();
				}
				if ("class".equals(name)) {
					continue; // No point in trying to set an object's class
				}
				if ((getPropertyUtils().isReadable(orig, name) || isRecord)
						&& getPropertyUtils().isWriteable(dest, name)) {
					try {
						final Object value = isRecord
								? RecordReflectUtils.componentValue(orig,
										new RecComponent(name, ((RecComponent) origDescriptor).type(), -1))
								: getSimpleProperty(orig, name);
						copyProperty(dest, name, value);
					} catch (final NoSuchMethodException e) {
						// Should not happen
					}
				}
			}
		}
	}

	public <T> T copyProperties(final Class<T> dest, final Object orig, Map<String, Object> map)
			throws IllegalAccessException, InvocationTargetException {

		// Validate existence of the specified beans
		if (dest == null) {
			throw new IllegalArgumentException("No destination bean specified");
		}
		if (orig == null) {
			throw new IllegalArgumentException("No origin bean specified");
		}
		if (log.isDebugEnabled()) {
			log.debug("BeanUtils.copyProperties(" + dest + ", " + orig + ")");
		}

		RecComponent[] recordComponents = RecordInvokeUtils.recordComponents(dest, null);
		Object[] arr = new Object[recordComponents.length];
		int index = 0;
		boolean mapNotNull = map != null;
		for (RecComponent recordComponent : recordComponents) {
			Class<?> recordComponentClass = recordComponent.type() instanceof ParameterizedType
					? (Class<?>) ((ParameterizedType) recordComponent.type()).getRawType()
					: (Class<?>) recordComponent.type();
			arr[index] = ReflectionUtils.getDefaultValue(recordComponentClass);
			if (mapNotNull && map.containsKey(recordComponent.name())) {
				arr[index] = map.get(recordComponent.name());
			}
			// Copy the properties, converting as necessary
			else if (orig instanceof DynaBean) {
				final DynaProperty[] origDescriptors = ((DynaBean) orig).getDynaClass().getDynaProperties();
				for (DynaProperty origDescriptor : origDescriptors) {
					final String name = origDescriptor.getName();

					if (recordComponent.name().equals(name)) {
						// Need to check isReadable() for WrapDynaBean
						// (see Jira issue# BEANUTILS-61)
						if (getPropertyUtils().isReadable(orig, name) && getPropertyUtils().isWriteable(dest, name)) {
							final Object value = ((DynaBean) orig).get(name);
							arr[index] = value;
						}
					}
				}
			} else if (orig instanceof Map) {
				@SuppressWarnings("unchecked")
				// Map properties are always of type <String, Object>
				final Map<String, Object> propMap = (Map<String, Object>) orig;
				for (final Map.Entry<String, Object> entry : propMap.entrySet()) {
					final String name = entry.getKey();
					if (recordComponent.name().equals(name)) {
						if (getPropertyUtils().isWriteable(dest, name)) {
							arr[index] = entry.getValue();
						}
					}
				}
			} else /* if (orig is a standard JavaBean) */ {
				boolean isRecord = RecordInvokeUtils.isRecord(orig.getClass());
				final Object[] origDescriptors = isRecord ? RecordInvokeUtils.recordComponents(dest, null)
						: getPropertyUtils().getPropertyDescriptors(orig);

				for (Object origDescriptor : origDescriptors) {
//					@SuppressWarnings("preview")
//					final String name = (switch (origDescriptor) {
//					case PropertyDescriptor p -> p.getName();
//					case java.lang.reflect.RecordComponent r -> r.getName();
//					default -> throw new IllegalArgumentException("Unexpected value: " + origDescriptor);
//					});
					String name = null;
					if (origDescriptor instanceof PropertyDescriptor) {
						name = ((PropertyDescriptor) origDescriptor).getName();
					} else if (origDescriptor instanceof RecComponent) {
						name = ((RecComponent) origDescriptor).name();
					}
					if ("class".equals(name)) {
						continue; // No point in trying to set an object's class
					}
					if (recordComponent.name().equals(name)) {
						if ((getPropertyUtils().isReadable(orig, name) || isRecord)) {
							try {
								final Object value = isRecord
										? RecordReflectUtils.componentValue(orig,
												new RecComponent(name, ((RecComponent) origDescriptor).type(), -1))
										: getSimpleProperty(orig, name);
								arr[index] = value;
							} catch (final NoSuchMethodException e) {
								log.error(e.getMessage());
							}
						}
					}
				}
			}
			index++;
		}
		return (T) RecordInvokeUtils.invokeCanonicalConstructor(dest, arr);
	}

	public <T> T populate(final Class<T> recordClass, final Map<String, ? extends Object> properties) {
		if (properties == null) {
			return null;
		}
		if (log.isDebugEnabled()) {
			log.debug("BeanUtils.populate(" + recordClass + ", " + properties + ")");
		}
		int i = 0;
		RecComponent[] recordComponents = RecordInvokeUtils.recordComponents(recordClass, null);
		Object[] arr = new Object[recordComponents.length];
		for (RecComponent recordComponent : recordComponents) {
			final String name = recordComponent.name();
			Type type = recordComponent.type();
			Class<?> recordComponentClass = type instanceof ParameterizedType
					? (Class<?>) ((ParameterizedType) type).getRawType()
					: (Class<?>) type;
			Object value = Optional.ofNullable(properties.get(name))
					.map(v -> v.getClass().isArray() ? ((Object[]) v)[0] : v)
					.orElseGet(() -> ReflectionUtils.getDefaultValue(recordComponentClass)), newValue = null;
			if (recordComponentClass.isArray()) {
				if (value instanceof String) {
					newValue = getConvertUtils().convert((String) value, recordComponentClass.getComponentType());
					if (!newValue.getClass().isArray()) {
						newValue = convert(value, recordComponentClass);
					}
					if (!newValue.getClass().isArray()) {
						newValue = new String[] { (String) newValue };
					}
				} else if (value instanceof String[]) {
					newValue = getConvertUtils().convert(((String[]) value)[0],
							recordComponentClass.getComponentType());
				} else {
					newValue = convert(value, recordComponentClass.getComponentType());
				}
			} else {
				boolean isOptional = false;
				Class<?> clazz;
				if (recordComponentClass == Optional.class) {
					isOptional = true;
					clazz = (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
				} else {
					clazz = recordComponentClass;
				}
				if (value instanceof String) {
					if (isOptional) {
						newValue = Optional.of(getConvertUtils().convert((String) value, clazz));
					} else {
						newValue = getConvertUtils().convert((String) value, clazz);
					}
				} else if (value instanceof String[]) {
					newValue = getConvertUtils().convert(((String[]) value)[0], clazz);
				} else {
					if (isOptional) {
						newValue = value != null ? Optional.of(getConvertUtils().convert((String) value, clazz)) : null;
					} else {
						newValue = convert(value, clazz);
					}
				}
			}
			arr[i] = newValue;
			i++;
		}
		T record = (T) RecordInvokeUtils.invokeCanonicalConstructor(recordClass, arr);
		return record;
	}

}
