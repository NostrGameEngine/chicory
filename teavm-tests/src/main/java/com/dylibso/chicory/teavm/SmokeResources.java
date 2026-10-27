package com.dylibso.chicory.teavm;

import java.util.Collection;
import org.teavm.extension.spi.resources.ResourcesPolicy;

/**
 * Bundles the precompiled module metadata for both TeaVM backends.
 */
public final class SmokeResources implements ResourcesPolicy {
    @Override
    public String[] supplyResources(Collection<? extends String> availableClassNames) {
        return new String[] {"com/dylibso/chicory/teavm/PrecompiledSmoke.meta"};
    }
}
