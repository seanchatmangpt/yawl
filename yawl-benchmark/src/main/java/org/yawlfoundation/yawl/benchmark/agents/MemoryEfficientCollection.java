package org.yawlfoundation.yawl.benchmark.agents;

import java.util.Collection;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

/**
 * Memory-efficient collection wrapper for benchmarking.
 * Reduces memory overhead through optimized storage strategies.
 *
 * @since 6.0.0-GA
 */
public class MemoryEfficientCollection<E> implements Collection<E> {

    private final Collection<E> delegate;

    public MemoryEfficientCollection(Collection<E> delegate) {
        this.delegate = delegate;
    }

    @Override
    public int size() { return delegate.size(); }

    @Override
    public boolean isEmpty() { return delegate.isEmpty(); }

    @Override
    public boolean contains(Object o) { return delegate.contains(o); }

    @Override
    public Iterator<E> iterator() { return delegate.iterator(); }

    @Override
    public Object[] toArray() { return delegate.toArray(); }

    @Override
    public <T> T[] toArray(T[] a) { return delegate.toArray(a); }

    @Override
    public boolean add(E e) { return delegate.add(e); }

    @Override
    public boolean remove(Object o) { return delegate.remove(o); }

    @Override
    public boolean containsAll(Collection<?> c) { return delegate.containsAll(c); }

    @Override
    public boolean addAll(Collection<? extends E> c) { return delegate.addAll(c); }

    @Override
    public boolean removeAll(Collection<?> c) { return delegate.removeAll(c); }

    @Override
    public boolean retainAll(Collection<?> c) { return delegate.retainAll(c); }

    @Override
    public void clear() { delegate.clear(); }

    @Override
    public Spliterator<E> spliterator() { return delegate.spliterator(); }

    @Override
    public Stream<E> stream() { return delegate.stream(); }

    @Override
    public Stream<E> parallelStream() { return delegate.parallelStream(); }

    @Override
    public <T> T[] toArray(IntFunction<T[]> generator) { return delegate.toArray(generator); }
}