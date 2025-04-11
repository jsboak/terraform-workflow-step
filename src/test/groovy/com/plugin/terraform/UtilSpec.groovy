package com.plugin.terraform

import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.core.storage.StorageTree
import org.rundeck.storage.api.PathUtil
import org.rundeck.storage.api.Resource
import com.dtolabs.rundeck.core.storage.ResourceMeta
import org.rundeck.storage.api.StorageException
import spock.lang.Specification
import com.dtolabs.rundeck.core.execution.ExecutionListener
import com.dtolabs.rundeck.plugins.PluginLogger


class UtilSpec extends Specification {

    String fakeKeyPath = "keys/fake"
    String providerPassword = "providerPassword"

    def "Test Correct StorageTree Path"() {

        def storageTree = Mock(StorageTree)
        def resource = Mock(Resource) {
            getContents() >> Mock(ResourceMeta) {
                writeContent(_ as ByteArrayOutputStream) >> { args ->
                    args[0].write(providerPassword.bytes)
                    return 13L
                }
            }
        }
        storageTree.getResource(fakeKeyPath) >> resource

        def executionListener = Mock(ExecutionListener)
        def executionContext = Mock(ExecutionContext) {
            getExecutionListener() >> executionListener
            getStorageTree() >> storageTree
        }
        def context = Mock(PluginStepContext) {
            getExecutionContext() >> executionContext
            getLogger() >> Mock(PluginLogger)
            getFrameworkProject() >> "frameworkProject"
        }

        when: "getPasswordFromKeyStorage is invoked"
        def password = Util.getPasswordFromKeyStorage(fakeKeyPath, context)

        then: "the expected password is returned"
        password == providerPassword

    }

    def "getPasswordFromKeyStorage throws StorageException on error"() {
        given:
        // Stub a StorageTree that throws an exception when getResource is called.
        def fakeStorageTree = Stub(StorageTree) {
            getResource("keys/nonexistent") >> { throw  StorageException.readException(PathUtil.asPath("keys/nonexistent"),"Resource not found") }
        }
        def fakeExecutionContext = Stub(ExecutionContext) {
            getStorageTree() >> fakeStorageTree
        }
        def context = Stub(PluginStepContext) {
            getExecutionContext() >> fakeExecutionContext
        }

        when:
        Util.getPasswordFromKeyStorage("keys/nonexistent", context)

        then:
        StorageException e = thrown()
        e.message.contains("Resource not found")
    }
}
