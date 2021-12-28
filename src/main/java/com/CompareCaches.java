package com;

import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import store.CacheLibrary;
import store.cache.index.Index;
import store.cache.index.OSRSIndices;
import store.cache.index.archive.Archive;
import store.cache.index.archive.file.File;
import store.progress.AbstractProgressListener;
import store.progress.ProgressListener;
import utility.XTEASManager;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * For those curious
 *
 * @version 28/12/21
 * @author Shadowrs r-s@Shadowy tardisfan121@gmail.com
 */
@Slf4j
public class CompareCaches {

    static HashMap<String, Logger> logs = new HashMap<>();


    public static ProgressListener progressListener = new AbstractProgressListener() {

        @Override
        public void finish(String title, String message) {

        }

        @Override
        public void change(double progress, String message) {

        }

    };

    public static void main(String[] args) {
        loadconfig();
        for (OSRSIndices value : OSRSIndices.values()) {
            Logger analytics = LoggerFactory.getLogger(value.name());
            logs.put(value.name(), analytics);
        }

        progressListener.notify(0, "Initializing into cache "+Paths.get(config.getString("into_cache")).toFile().getAbsolutePath());

        CacheLibrary intoCache = CacheLibrary.createUncached(Paths.get(config.getString("into_cache")).toFile().getAbsolutePath());

        progressListener.notify(0, "Initializing from cache "+Paths.get(config.getString("from_cache")).toFile().getAbsolutePath());

        CacheLibrary fromCache = CacheLibrary.createUncached(Paths.get(config.getString("from_cache")).toFile().getAbsolutePath());

        progressListener.notify(0, "Initializing XTEA manager with format "+config.getString("xteas_format")+" and file "+Paths.get(config.getString("xteas_file")).toFile().getAbsolutePath());
        new XTEASManager.MODERN() {
            {
                parserType = XTEASManager.XTEAParserType.valueOf(config.getString("xteas_format"));
            }
            @Override
            public String filePath() {
                return Paths.get(config.getString("xteas_file")).toFile().getAbsolutePath();
            }
        }.load();

       // System.exit(0); // when testing config file loading paths

        //extracted(intoCache, fromCache, OSRSIndices.SKINS); // test single to logfile

        // do em all
        for (OSRSIndices value : OSRSIndices.values()) {
            extracted(intoCache, fromCache, value);
        }
    }

    public static com.typesafe.config.Config config;

    private static void loadconfig() {
        java.io.File confFile = Paths.get("app.conf").toFile();
        log.info("Config path: {}", confFile.getAbsolutePath());
        config = ConfigFactory.systemProperties().withFallback(ConfigFactory.parseFileAnySyntax(confFile));
    }

    private static void extracted(CacheLibrary intoCache, CacheLibrary fromCache, OSRSIndices indice) {
        Logger idxLogger = LoggerFactory.getLogger(indice.name());
        Index intoIdx = intoCache.getIndex(indice.ordinal());
        Index fromIdx = fromCache.getIndex(indice.ordinal());

        Set<Integer> archivesIds = new HashSet<>();
        idxLogger.info("archives in {}: {} vs {}", indice, intoIdx.getArchives().length, fromIdx.getArchives().length);
        for (int archiveId : intoIdx.getArchiveIds()) {
            archivesIds.add(archiveId);
        }
        for (int archiveId : fromIdx.getArchiveIds()) {
            archivesIds.add(archiveId);
        }
        idxLogger.info("stored {} archive ids", archivesIds.size());

        for (Integer archiveId : archivesIds) {
            Archive intoArch = intoIdx.getArchive(archiveId);
            Archive fromArch = fromIdx.getArchive(archiveId);

            if (intoArch == null) {
                idxLogger.info("idx {} new archive: {} with {} file ids", indice, archiveId, fromArch.getFileIds().length);
                continue;
            }
            int intoCount = intoArch.getFiles() == null ? 0 : fromArch.getFiles().length;
            int fromCount = fromArch.getFiles() == null ? 0 : fromArch.getFiles().length;

            if (intoCount != fromCount)
                idxLogger.info("idx {} archive {} has file change count {} vs {}", indice, archiveId, fromCount, intoCount);

            //idxLogger.info("idx {} archive {} has {} files", indice, archiveId, count);
           // idxLogger.info("idx {} archive {} has {} files", indice, archiveId, count);

            for (int fileId : fromArch.getFileIds()) {
                File fromFile = fromArch.getFile(fileId);
                File intoFile = intoArch.getFile(fileId);
                boolean removed = intoFile != null && fromFile.getData() == null && intoFile.getData() != null;
                boolean bothActive = fromFile != null && intoFile != null;
                boolean bothMissingData = bothActive && intoFile.getData() == null && fromFile.getData() == null;
                boolean missingData = bothActive && !bothMissingData && (intoFile.getData()==null || fromFile.getData()==null);
                boolean changed = intoFile != null && fromFile.getData() != null && intoFile.getData() != null && fromFile.getData().length != intoFile.getData().length;
                if (intoFile == null) {
                    idxLogger.info("idx {} archive {} has new file {}", indice, archiveId, fileId);
                }
                else if (removed) {
                    idxLogger.info("idx {} archive {} file {} was deleted", indice, archiveId, fileId);
                }
                else if (missingData) {
                    idxLogger.info("idx {} archive {} file {} exists but data was deleted {} vs {}", indice, archiveId, fileId, intoFile.getData(), fromFile.getData());
                }
                else if (changed) {
                    idxLogger.info("idx {} archive {} file {} length changed from old {} to new {} by {} bytes", indice, archiveId, fileId, intoFile.getData().length, fromFile.getData().length, intoFile.getData().length-fromArch.getData().length);
                }
            }
        }

    }

}