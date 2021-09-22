package edu.vanderbilt.grader.tools.utils

import edu.vanderbilt.grader.tools.ui.Repo
import edu.vanderbilt.grader.tools.utils.ThreadUtils.assertUiThread
import java.lang.reflect.Field
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaGetter

typealias Property<T> = KProperty1<T, Any?>

/**
 * Currently on supports @TableColumnProp annotation on class T and
 * does not recognize those annotations declared by super classes.
 */
open class ReflectionTableModel<T : Any>(clazz: Class<T>) : ObjectTableModel<T>() {
    private val columnInfoMap: MutableMap<Int, ColumnInfo> = mutableMapOf()

    init {
        try {
            // Limit @TableColumnProp recognition to declared member properties.
            clazz.kotlin.declaredMemberProperties
                .filter { it.visibility != KVisibility.PRIVATE }
                .filter { it.findAnnotation<TableColumnProp>() != null }
                .map { prop ->
                    prop.findAnnotation<TableColumnProp>()?.apply {
                        columnInfoMap[index] =
                            ColumnInfo(
                                prop = prop,
                                index = index,
                                name = if (value == "default") prop.name else value,
                                editable = editable
                            )
                    }
                }
        } catch (e: Exception) {
            throw Exception(e)
        }
    }

    fun propAt(column: Int): KProperty<*> {
        assertUiThread()
        return columnInfoMap[column]!!.prop
    }

    open fun fieldToIndex(prop: Property<T>): Int {
        assertUiThread()
        return Repo::class.declaredMemberProperties
            .firstOrNull { it.name == prop.name }
            ?.findAnnotation<TableColumnProp>()?.index ?: -1
    }

    fun Property<T>.toColumn(): Int {
        assertUiThread()
        return Repo::class.declaredMemberProperties
            .firstOrNull { it.name == name }
            ?.findAnnotation<TableColumnProp>()?.index ?: -1
    }

    override fun getValueAt(row: T, column: Int): Any? {
        assertUiThread()
        return try {
            columnInfoMap[column]?.prop?.getter?.call(row)
        } catch (e: Exception) {
            throw Exception(e)
        }
    }

    override fun setValueAt(value: Any?, row: T, column: Int) {
        assertUiThread()
        try {
            (columnInfoMap[column]?.prop as? KMutableProperty)?.setter?.call(row, value)
            fireTableCellUpdated(rows.indexOf(row), column)
        } catch (e: Exception) {
            throw Exception(e)
        }
    }

    /** Convenience function that maps the object [prop] to a column. */
    fun setValueAt(value: Any?, row: T, prop: Property<T>) {
        assertUiThread()
        setValueAt(value, row, fieldToIndex(prop))
    }

    /**
     * Kotlin data class fields are declared as private final.
     * Note that setValueAt is not implemented using reflection
     * so this class will not bypass the Kotlin data class
     * immutability contract by allowing modification of any fields.
     */
    private fun Field.getPrivateFinal(any: Any): Any {
        val wasAccessible: Boolean = isAccessible
        isAccessible = true
        val value = get(any)
        isAccessible = wasAccessible
        return value
    }

    override fun getColumnCount(): Int {
        assertUiThread()
        return columnInfoMap.size
    }

    override fun getColumnName(column: Int): String {
        assertUiThread()
        return columnInfoMap[column]?.name ?: throw Exception("No column found for index $column")
    }

    /** Must return the type as a boxed data type. */
    override fun getColumnClass(column: Int): Class<*>? {
        assertUiThread()
        return columnInfoMap[column]?.prop?.javaGetter?.returnType?.kotlin?.javaObjectType
    }

    override fun isCellEditable(row: Int, column: Int): Boolean {
        assertUiThread()
        return columnInfoMap[column]?.let {
            it.editable && it.prop is KMutableProperty<*>
        } == true
    }

    private data class ColumnInfo(
        val prop: KProperty<*>,
        val index: Int,
        val name: String,
        val editable: Boolean
    ) {
        val propertyName: String = prop.name
        override fun toString(): String {
            val clazz = prop.javaGetter?.returnType?.kotlin?.javaObjectType
            val isEditable = editable && prop is KMutableProperty<*>

            return "ColumnInfo(" +
                    "prop=$prop, " +
                    "index=$index, " +
                    "name='$name', " +
                    "editable=$editable, " +
                    "propertyName='$propertyName') -> " +
                    "class = $clazz, " +
                    "isEditable = $isEditable"
        }
    }
}
