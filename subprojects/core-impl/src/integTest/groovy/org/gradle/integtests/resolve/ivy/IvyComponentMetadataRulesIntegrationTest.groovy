/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve.ivy

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest

import static org.hamcrest.Matchers.containsString

class IvyComponentMetadataRulesIntegrationTest extends AbstractDependencyResolutionTest {
    def setup() {
        buildFile <<
"""
repositories {
    ivy {
        url "${ivyRepo.uri}"
    }
}

configurations { compile }

dependencies {
    compile 'org.test:projectA:1.0'
}

task resolve(type: Sync) {
    from configurations.compile
    into 'libs'
}
"""
    }

    def "rule is being passed correct, mutable metadata"() {
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus("release").publish()
        buildFile <<
"""
dependencies {
    componentMetadata {
        eachComponent { details ->
            assert details.id.group == "org.test"
            assert details.id.name == "projectA"
            assert details.id.version == "1.0"
            assert details.status == "release"
            assert details.statusScheme == ["integration", "milestone", "release"]

            details.status "silver" // verify that 'details' is enhanced
            assert details.status == "silver"

            details.statusScheme = ["bronze", "silver", "gold"]
            assert details.statusScheme == ["bronze", "silver", "gold"]
        }
    }
}
"""

        expect:
        succeeds 'resolve'
    }

    def "module with custom status can be resolved by adapting status scheme"() {
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus("silver").publish()
        buildFile <<
"""
dependencies {
    componentMetadata {
        eachComponent { details ->
            details.statusScheme = ["gold", "silver", "bronze"]
        }
    }
}
"""

        expect:
        succeeds 'resolve'
        file('libs').assertHasDescendants('projectA-1.0.jar')
    }

    def "resolve fails if status doesn't match default status scheme"() {
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus("silver").publish()

        expect:
        fails 'resolve'
        failure.assertThatCause(containsString("bad status: 'silver'"))
    }

    def "resolve fails if status doesn't match custom status scheme"() {
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus("silver").publish()
        buildFile <<
"""
dependencies {
    componentMetadata {
        eachComponent { details ->
            details.statusScheme = ["gold", "bronze"]
        }
    }
}
"""

        expect:
        fails 'resolve'
        failure.assertThatCause(containsString("bad status: 'silver'"))
    }

    def "rule can change status"() {
        ivyRepo.module('org.test', 'projectA', '1.0').withStatus("silver").publish()
        buildFile <<
"""
dependencies {
    componentMetadata {
        eachComponent { details ->
            details.status = "milestone"
        }
    }
}
"""

        expect:
        succeeds 'resolve'
    }
}
