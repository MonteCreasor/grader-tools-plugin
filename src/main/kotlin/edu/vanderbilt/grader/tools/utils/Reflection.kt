package admin

import org.junit.Assert
import java.lang.reflect.Field
import kotlin.reflect.KClass

inline fun <reified T> Any.value(): T? = getField("", T::class.java)

inline fun <reified T> Any.primitiveValue(type: KClass<*>): T {
    return javaClass.findField(type.javaPrimitiveType!!).let {
        val wasAccessible = isAccessible(it)
        it.isAccessible = true
        val result = it.get(this)
        it.isAccessible = wasAccessible
        result as T
    }
}

inline fun Any.isAccessible(field: Field): Boolean {
    return field.isAccessible
}

inline fun <reified T> Any.firstField(): Field? =
        javaClass.declaredFields.firstOrNull { field: Field ->
            field.type == T::class.java
        }

inline fun <reified T> Any.getField(name: String, type: Class<T>): T {
    return javaClass.findField(type, name).let {
        val wasAccessible = isAccessible(it)
        val wasAccessible1 = it.canAccess(this)

        it.isAccessible = true
        val result = it.get(this)
        it.isAccessible = wasAccessible
        result as T
    }
}

inline fun <reified T> Any.setField(name: String, value: T) {
    setField(value, T::class.java, name)
}

inline fun <reified T> T.injectInto(parent: Any, name: String = ""): T {
    val type = when (this) {
        is Int -> Int::class.javaPrimitiveType
        is Float -> Float::class.javaPrimitiveType
        is Double -> Double::class.javaPrimitiveType
        is Short -> Short::class.javaPrimitiveType
        else -> T::class.java
    }
    val field = parent::class.java.findField(type!!, name)
    parent.setField(this, type, field.name)
    return this
}

inline fun <reified T> Any.setJavaPrimitiveField(value: T, name: String) {
    val javaPrimitiveType = when (value) {
        is Int -> Int::class.javaPrimitiveType
        is Float -> Float::class.javaPrimitiveType
        is Double -> Double::class.javaPrimitiveType
        is Short -> Short::class.javaPrimitiveType
        else -> throw Exception("value is not a have an equivalent Java primitive type")
    }
    javaClass.findField(javaPrimitiveType!!, name).let {
        val wasAccessible = isAccessible(it)
        it.isAccessible = true
        it.set(this, value)
        it.isAccessible = wasAccessible
    }
}

inline fun <reified T> Any.setField(value: T) {
    javaClass.findField(T::class.java, "").let {
        val wasAccessible = isAccessible(it)
        it.isAccessible = true
        it.set(this, value)
        it.isAccessible = wasAccessible
    }
}

inline fun <reified T> Any.setField(value: T, type: Class<*>?, name: String = "") {
    javaClass.findField(type!!, name).let {
        val wasAccessible = isAccessible(it)
        it.isAccessible = true
        it.set(this, value)
        it.isAccessible = wasAccessible
    }
}

fun Class<*>.findField(type: Class<*>, name: String = ""): Field {
    try {
        return declaredFields.firstOrNull {
            val wasAccessible = isAccessible(it)
            try {
                it.isAccessible = true
                (name.isBlank() || it.name == name) && it.type == type
            } finally {
                it.isAccessible = wasAccessible
            }
        } ?: superclass!!.findField(type, name)
    } catch (e: Exception) {
        throw Exception("Class field $name with type $type does not exist")
    }
}

var Any.outerclass: Any
    get() = javaClass.superclass!!
    set(value) {
        val field = javaClass.getDeclaredField("this$0")
        val wasAccessible = isAccessible(field)
        field.isAccessible = true
        field.set(this, value)
        field.isAccessible = wasAccessible
    }

fun Any.reflectiveEquals(expected: Any): Boolean {
    val fields = javaClass.declaredFields
    val expectedFields = expected.javaClass.declaredFields
    Assert.assertEquals(expectedFields.size, fields.size)
    for (i in 0..fields.lastIndex) {
        if (fields[i] != expectedFields[i]) {
            return false
        }
    }

    return true
}

