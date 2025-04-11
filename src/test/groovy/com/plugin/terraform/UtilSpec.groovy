package com.plugin.terraform

import com.dtolabs.rundeck.core.execution.ExecutionContext
import com.dtolabs.rundeck.plugins.step.PluginStepContext
import com.dtolabs.rundeck.core.storage.StorageTree
import org.rundeck.storage.api.PathUtil
import org.rundeck.storage.api.Resource
import com.dtolabs.rundeck.core.storage.ResourceMeta
import org.rundeck.storage.api.StorageException
import spock.lang.Specification

class UtilSpec extends Specification {

    def "getPasswordFromKeyStorage returns correct secret"() {
        given:
        // Stub a fake ResourceMeta that writes "testSecret" into the output stream.
        def fakeResourceMeta = Stub(ResourceMeta) {
            writeContent(_ as ByteArrayOutputStream) >> { ByteArrayOutputStream os ->
                os.write("testSecret".bytes)
            }
        }
        // Stub a fake Resource that returns the fakeResourceMeta.
        def fakeResource = Stub(Resource) {
            getContents() >> fakeResourceMeta
        }
        // Create a stub for StorageTree (from com.dtolabs.rundeck.core.storage) so it returns the fakeResource.
        def fakeStorageTree = Stub(StorageTree) {
            getResource("keys/dummy") >> fakeResource
        }
        // Build a fake ExecutionContext returning our fake StorageTree.
        def fakeExecutionContext = Stub(ExecutionContext) {
            getStorageTree() >> fakeStorageTree
        }
        // Create a PluginStepContext stub inline.
        def context = Stub(PluginStepContext) {
            getExecutionContext() >> fakeExecutionContext
        }

        when:
        def secret = Util.getPasswordFromKeyStorage("keys/dummy", context)

        then:
        secret == "testSecret"
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
