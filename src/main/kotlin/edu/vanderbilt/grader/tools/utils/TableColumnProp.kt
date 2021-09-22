package edu.vanderbilt.grader.tools.utils

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY)
/**
 * When value is set to "default", the property name will be
 * used, and if set to "", no column name will be displayed
 */
annotation class TableColumnProp(val index: Int, val value: String = "default", val editable: Boolean = false)