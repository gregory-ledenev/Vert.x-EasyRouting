/*
 *
 * Copyright 2025 Gregory Ledenev (gregory.ledenev37@gmail.com)
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the “Software”), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * /
 */

package com.gl.vertx.easyrouting.annotations;

import io.vertx.ext.web.RoutingContext;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class TemplateModel implements Map<String, Object> {
    private final RoutingContext ctx;

    public TemplateModel(RoutingContext aCtx) {
        ctx = aCtx;
    }

    @Override
    public int size() {
        return ctx.data().size();
    }

    @Override
    public boolean isEmpty() {
        return ctx.data().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return ctx.data().containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return ctx.data().containsValue(value);
    }

    @Override
    public Object get(Object key) {
        return ctx.get((String) key);
    }

    @Override
    public Object put(String key, Object value) {
        return ctx.put((String) key, value);
    }

    @Override
    public Object remove(Object key) {
        return ctx.remove((String) key);
    }

    @Override
    public void putAll(Map<? extends String, ?> m) {

    }

    @Override
    public void clear() {

    }

    @Override
    public Set<String> keySet() {
        return ctx.data().keySet();
    }

    @Override
    public Collection<Object> values() {
        return ctx.data().values();
    }

    @Override
    public Set<Entry<String, Object>> entrySet() {
        return ctx.data().entrySet();
    }
}
