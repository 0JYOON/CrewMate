package com.example.crewmate.labs

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabsManagerTest {

    private class FakeSharedPreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()
        private val listeners = linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }

            override fun commit(): Boolean {
                apply()
                return true
            }

            override fun apply() {
                if (clearAll) map.clear()
                removals.forEach { map.remove(it) }
                pending.forEach { (k, v) -> map[k] = v }
                val changedKeys = (removals + pending.keys).toSet()
                listeners.forEach { listener ->
                    changedKeys.forEach { key -> listener.onSharedPreferenceChanged(this@FakeSharedPreferences, key) }
                }
            }
        }

        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.add(listener)
        }

        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.remove(listener)
        }
    }

    @Test
    fun `registry ids are unique and lookup works`() {
        val ids = LabFeature.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(LabFeature.ROUTE_DIAGRAM, LabFeature.fromId("route_diagram"))
        assertEquals(LabFeature.PAY_STATEMENT, LabFeature.fromId("pay_statement"))
        assertNull(LabFeature.fromId("unknown_feature"))
    }

    @Test
    fun `unimplemented features stay disabled even if toggled`() {
        val prefs = FakeSharedPreferences()
        val unimplemented = LabFeature.entries.first { !it.isImplemented }

        LabsManager.setEnabled(prefs, unimplemented, true)
        assertFalse(LabsManager.isEnabled(prefs, unimplemented))
    }

    @Test
    fun `resetAll clears all lab preference keys`() {
        val prefs = FakeSharedPreferences()
        val editor = prefs.edit()
        LabFeature.entries.forEach { feature ->
            editor.putBoolean(LabsManager.prefKey(feature), true)
        }
        editor.apply()

        LabsManager.resetAll(prefs)
        LabFeature.entries.forEach { feature ->
            assertFalse(prefs.contains(LabsManager.prefKey(feature)))
        }
    }

    @Test
    fun `labs features stay disabled until labs menu is unlocked`() {
        val prefs = FakeSharedPreferences()
        assertFalse(LabsManager.isLabsUnlocked(prefs))

        LabsManager.setEnabled(prefs, LabFeature.ROUTE_DIAGRAM, true)
        assertFalse(LabsManager.isEnabled(prefs, LabFeature.ROUTE_DIAGRAM))

        LabsManager.setLabsUnlocked(prefs, true)
        assertTrue(LabsManager.isLabsUnlocked(prefs))
        assertTrue(LabsManager.isEnabled(prefs, LabFeature.ROUTE_DIAGRAM))

        LabsManager.setLabsUnlocked(prefs, false)
        assertFalse(LabsManager.isEnabled(prefs, LabFeature.ROUTE_DIAGRAM))
    }

    @Test
    fun `runLabSafely catches exceptions without crashing`() {
        val failure = LabsManager.runLabSafely(LabFeature.PAY_STATEMENT) {
            error("simulated parser error")
        }
        assertTrue(failure.isFailure)
        assertNotNull(failure.exceptionOrNull())

        val success = LabsManager.runLabSafely(LabFeature.ROUTE_DIAGRAM) { 42 }
        assertTrue(success.isSuccess)
        assertEquals(42, success.getOrNull())
    }
}
