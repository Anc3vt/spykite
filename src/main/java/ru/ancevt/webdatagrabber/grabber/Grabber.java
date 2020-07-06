package ru.ancevt.webdatagrabber.grabber;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import ru.ancevt.util.Range;
import ru.ancevt.webdatagrabber.api.IListRequester;
import ru.ancevt.webdatagrabber.api.IRequester;
import ru.ancevt.webdatagrabber.config.Config;
import ru.ancevt.webdatagrabber.ds.IEntity;
import ru.ancevt.webdatagrabber.log.Log;
import ru.ancevt.webdatagrabber.output.IOutput;

public class Grabber<T extends IEntity> {

    private final static int MAX_PAGE_REQUEST_TRIES = 10;
    private final static int MAX_THREADS = 50;

    private final IOutput output;
    private final List<IEntity> entities;
    private final Class listRequesterClass;
    private final Class requesterClass;

    private int pageFrom;
    private int pageTo;

    private int totalCount;

    private int nullsInRow;

    private final Config config;

    public Grabber(Config config, IOutput output, Class listRequesterClass, Class requesterClass) {
        this.config = config;
        this.output = output;
        this.listRequesterClass = listRequesterClass;
        this.requesterClass = requesterClass;

        entities = new ArrayList<>();
    }

    public final void startByIdRanges(List<Range> ranges) {
        ranges.stream().forEach((r) -> {
            final int[] ids = getIdsFromRange(r);
            Log.getLogger().info("Load id range: " + r.toString());
            processByIds(ids);
        });

    }

    private int[] getIdsFromRange(Range range) {
        final int f = range.getFrom();
        final int t = range.getTo();

        final int[] result = new int[t - f];

        int c = 0;
        for (int i = f; i < t; i++) {
            result[c++] = i;
        }

        return result;
    }

    public final void startByPageRange(int pageFrom, int pageTo) throws IOException {
        this.pageFrom = pageFrom;
        this.pageTo = pageTo;

        search(pageFrom, MAX_PAGE_REQUEST_TRIES);
    }

    private void search(int page, int tries) {
        try {
            if (tries == 0) {
                if (Log.getLogger().isInfoEnabled()) {
                    Log.getLogger().info(String.format("%d attempts to request are exhausted", MAX_PAGE_REQUEST_TRIES));
                }
                exit();
                return;
            }

            if (page > pageTo) {
                if (Log.getLogger().isInfoEnabled()) {
                    Log.getLogger().info(String.format("Exiting caused by page is %d.", page));
                }
                exit();
                return;
            }

            Log.getLogger().info("Loading page " + page + "...");

            final IListRequester listRequester = createListRequester(config, listRequesterClass);
            final int status = listRequester.request(page);

            if (Log.getLogger().isDebugEnabled()) {
                Log.getLogger().debug("Status: " + status);
            }

            if (status == 200) {
                int[] ids = listRequester.getIds();

                if (Log.getLogger().isDebugEnabled()) {
                    Log.getLogger().debug("Entities detected on page: " + ids.length);
                }

                if (ids.length > 0) {
                    processByIds(ids);
                }

                if (ids.length == 0) {

                    if (Log.getLogger().isInfoEnabled()) {
                        Log.getLogger().info(String.format("Empty page %d, exiting...", page));
                    }
                    exit();

                } else {
                    search(page + 1, MAX_PAGE_REQUEST_TRIES);
                }

            } else {
                if (Log.getLogger().isDebugEnabled()) {
                    Log.getLogger().debug("Requesting page " + page + " status " + status + ", retrying...");
                }
                search(page, tries - 1);
            }
        } catch (IOException ex) {
            Log.getLogger().error("I/O error, tries: " + tries, ex);
            search(page, tries - 1);
        }

    }

    private void processByIds(int[] ids) {
        final ThreadWrapper<T> wrapper = new ThreadWrapper();

        for (int i = 0; i < ids.length; i++) {
            final int id = ids[i];
            final IRequester requester = createRequester(config, requesterClass);
            final LoadEntityThread<T> thread = new LoadEntityThread<>(requester, id);

            wrapper.add(thread);

            final int maxThreads = config.getThreads();

            if (wrapper.size() >= maxThreads || (ids.length < maxThreads && i == ids.length - 1)) {
                wrapper.start();
                wrapper.join();

                int currentCount = 0;

                final T[] ets = (T[]) wrapper.getResults();

                for (int j = 0; j < ets.length; j++) {
                    final IEntity entity = ets[j];

                    if (entity != null) {
                        if (!alreadyHas(entity)) {
                            currentCount++;
                            totalCount++;
                            entities.add(entity);
                            output.output(entity);
                            if (Log.getLogger().isDebugEnabled()) {
                                Log.getLogger().debug("Loaded entity " + entity.getShortDisplayName());
                            }
                        } else {
                            if (Log.getLogger().isTraceEnabled()) {
                                Log.getLogger().trace("Skipped entity:" + entity.getShortDisplayName());
                            }
                        }
                    } else {
                        if (Log.getLogger().isTraceEnabled()) {
                            Log.getLogger().trace("entity " + ids[i + j] + " is null");
                        }
                    }
                }

                if (currentCount > 0) {
                    nullsInRow = 0;
                    if (Log.getLogger().isInfoEnabled()) {
                        Log.getLogger().info("Loaded entities " + currentCount + ", total: " + totalCount);
                    }
                } else {
                    nullsInRow++;
                    final int attempts = config.getNullsInRowToExit() - nullsInRow;
                    Log.getLogger().info(ids[i] + "-" + ids[i + maxThreads - 1] + " id subrange is empty. Attempts before exit: " + attempts);
                    if (attempts == 0) {
                        return;
                    }
                }

                wrapper.clear();
            }
        }

    }

    private static IListRequester createListRequester(Config config, Class listRequesterClass) {
        try {
            final Constructor<IListRequester> listRequestClassConstructor = listRequesterClass.getConstructor(Config.class);

            try {
                final IListRequester listRequester = listRequestClassConstructor.newInstance(config);

                return listRequester;
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.getLogger().error("Reflection error", ex);
            }
        } catch (SecurityException | NoSuchMethodException ex) {
            Log.getLogger().error("Reflection error", ex);
        }

        return null;
    }

    private static IRequester createRequester(Config config, Class requesterClass) {
        try {
            final Constructor<IRequester> requesterClassConstructor = requesterClass.getConstructor(Config.class);

            try {
                final IRequester requester = requesterClassConstructor.newInstance(config);

                return requester;
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                Log.getLogger().error("Reflection error", ex);
            }
        } catch (NoSuchMethodException | SecurityException ex) {
            Log.getLogger().error("Reflection error", ex);
        }

        return null;
    }

    private boolean alreadyHas(IEntity v) {
        return entities.stream().anyMatch((entitie) -> (entitie.getId() == v.getId()));
    }

    public int getTotalCount() {
        return totalCount;
    }

    private void exit() {
        Log.getLogger().info("Exit, total entities loaded: " + totalCount);
    }

}
