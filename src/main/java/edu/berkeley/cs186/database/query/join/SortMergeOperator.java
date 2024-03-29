package edu.berkeley.cs186.database.query.join;

import java.util.*;

import edu.berkeley.cs186.database.TransactionContext;
import edu.berkeley.cs186.database.common.iterator.BacktrackingIterator;
import edu.berkeley.cs186.database.query.JoinOperator;
import edu.berkeley.cs186.database.query.MaterializeOperator;
import edu.berkeley.cs186.database.query.QueryOperator;
import edu.berkeley.cs186.database.query.SortOperator;
import edu.berkeley.cs186.database.table.Record;

public class SortMergeOperator extends JoinOperator {
    public SortMergeOperator(QueryOperator leftSource,
                             QueryOperator rightSource,
                             String leftColumnName,
                             String rightColumnName,
                             TransactionContext transaction) {
        super(prepareLeft(transaction, leftSource, leftColumnName),
              prepareRight(transaction, rightSource, rightColumnName),
              leftColumnName, rightColumnName, transaction, JoinType.SORTMERGE);
        this.stats = this.estimateStats();
    }

    /**
     * If the left source is already sorted on the target column then this
     * returns the leftSource, otherwise it wraps the left source in a sort
     * operator.
     */
    private static QueryOperator prepareLeft(TransactionContext transaction,
                                             QueryOperator leftSource,
                                             String leftColumn) {
        leftColumn = checkSchemaForColumn(leftSource.getSchema(), leftColumn);
        if (leftSource.sortedBy().contains(leftColumn)) return leftSource;
        return new SortOperator(transaction, leftSource, leftColumn);
    }

    /**
     * If the right source isn't sorted, wraps the right source in a sort
     * operator. Otherwise, if it isn't materialized, wraps the right source in
     * a materialize operator. Otherwise, simply returns the right source. Note
     * that the right source must be materialized since we may need to backtrack
     * over it, unlike the left source.
     */
    private static QueryOperator prepareRight(TransactionContext transaction,
                                              QueryOperator rightSource,
                                              String rightColumn) {
        rightColumn = checkSchemaForColumn(rightSource.getSchema(), rightColumn);
        if (!rightSource.sortedBy().contains(rightColumn)) {
            return new SortOperator(transaction, rightSource, rightColumn);
        } else if (!rightSource.materialized()) {
            return new MaterializeOperator(rightSource, transaction);
        }
        return rightSource;
    }

    @Override
    public Iterator<Record> iterator() {
        return new SortMergeIterator();
    }

    @Override
    public List<String> sortedBy() {
        return Arrays.asList(getLeftColumnName(), getRightColumnName());
    }

    @Override
    public int estimateIOCost() {
        //does nothing
        return 0;
    }

    /**
     * An implementation of Iterator that provides an iterator interface for this operator.
     *    See lecture slides.
     *
     * Before proceeding, you should read and understand SNLJOperator.java
     *    You can find it in the same directory as this file.
     *
     * Word of advice: try to decompose the problem into distinguishable sub-problems.
     *    This means you'll probably want to add more methods than those given (Once again,
     *    SNLJOperator.java might be a useful reference).
     *
     */
    private class SortMergeIterator implements Iterator<Record> {
        /**
        * Some member variables are provided for guidance, but there are many possible solutions.
        * You should implement the solution that's best for you, using any member variables you need.
        * You're free to use these member variables, but you're not obligated to.
        */
        private Iterator<Record> leftIterator;
        private BacktrackingIterator<Record> rightIterator;
        private Record leftRecord;
        private Record nextRecord;
        private Record rightRecord;
        private boolean marked;

        private SortMergeIterator() {
            super();
            leftIterator = getLeftSource().iterator();
            rightIterator = getRightSource().backtrackingIterator();
            rightIterator.markNext();

            if (leftIterator.hasNext() && rightIterator.hasNext()) {
                leftRecord = leftIterator.next();
                rightRecord = rightIterator.next();
            }

            this.marked = false;

            try {
                fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }
        }

        /**
         * Checks if there are more record(s) to yield
         *
         * @return true if this iterator has another record to yield, otherwise false
         */
        @Override
        public boolean hasNext() {
            return nextRecord != null;
        }

        /**
         * Yields the next record of this iterator.
         *
         * @return the next Record
         * @throws NoSuchElementException if there are no more Records to yield
         */
        @Override
        public Record next() {
            if (!this.hasNext()) throw new NoSuchElementException();
            Record nextRecord = this.nextRecord;
            try {
                this.fetchNextRecord();
            } catch (NoSuchElementException e) {
                this.nextRecord = null;
            }
            return nextRecord;
        }

        /**
         * Fetches the next record to return, and sets nextRecord to it. If
         * there are no more records to return, a NoSuchElementException should
         * be thrown.
         *
         * @throws NoSuchElementException if there are no more records to yield
         */
        private void fetchNextRecord() {
            // TODO(proj3_part1): implement
            if (leftRecord == null) {
                throw new NoSuchElementException();
            }
            if (rightRecord == null && !marked) {
                throw new NoSuchElementException();
            }

            if (rightRecord == null) {
                rightIterator.reset();
                advanceRightIterator();
                advanceLeftIterator();
            }
            nextRecord = null;
            do {
                if (!marked) {
                    while (compare(leftRecord, rightRecord) == -1) {
                        advanceLeftIterator();
                    }
                    while (compare(leftRecord, rightRecord) == 1) {
                        advanceRightIterator();
                    }
                    rightIterator.markPrev();
                    marked = true;
                }

                if (compare(leftRecord, rightRecord) == 0) {
                    nextRecord = leftRecord.concat(rightRecord);
                    if (rightIterator.hasNext()) {
                        rightRecord = rightIterator.next();
                    } else {
                        rightRecord = null;
                    }
                } else {
                    rightIterator.reset();
                    rightRecord = rightIterator.next();
                    advanceLeftIterator();
                    marked = false;
                }
            } while (nextRecord == null);
        }

        private void advanceRightIterator() {
            if (rightIterator.hasNext()) {
                rightRecord = rightIterator.next();
            } else {
                rightRecord = null;
                throw new NoSuchElementException();
            }
        }

        private void advanceLeftIterator() {
            if (leftIterator.hasNext()) {
                leftRecord = leftIterator.next();
            } else {
                leftRecord = null;
                throw new NoSuchElementException();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
