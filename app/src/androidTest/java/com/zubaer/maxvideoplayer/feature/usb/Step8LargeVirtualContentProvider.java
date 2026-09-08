package com.zubaer.maxvideoplayer.feature.usb;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Test-only provider that exposes a seekable >3 GB sparse file without allocating 3 GB. */
public final class Step8LargeVirtualContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.zubaer.maxvideoplayer.step8virtual";
    public static final long VIRTUAL_LENGTH = 3_221_225_473L;
    public static final long VERIFICATION_OFFSET = 2_147_483_648L + 33_333L;
    public static final int VERIFICATION_LENGTH = 8192;
    public static final Uri URI =
            Uri.parse("content://" + AUTHORITY + "/removable/step8-usb-3gb.mp4");
    public static final Uri REMOVED_URI =
            Uri.parse("content://" + AUTHORITY + "/removed/step8-usb-3gb.mp4");

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public String getType(Uri uri) {
        return "video/mp4";
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return metadataCursor(projection);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            Bundle queryArgs,
            CancellationSignal cancellationSignal) {
        return metadataCursor(projection);
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (REMOVED_URI.equals(uri)) {
            throw new FileNotFoundException("Virtual removable source disconnected");
        }
        if (!URI.equals(uri)) {
            throw new FileNotFoundException("Unknown Step 8 virtual source");
        }
        if (!"r".equals(mode)) {
            throw new FileNotFoundException("Step 8 virtual source is read-only");
        }
        return ParcelFileDescriptor.open(ensureSparseFixture(), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    private Cursor metadataCursor(String[] projection) {
        final String[] columns =
                projection == null || projection.length == 0
                        ? new String[] {"_display_name", "_size"}
                        : projection;
        final MatrixCursor cursor = new MatrixCursor(columns);
        final MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : columns) {
            if ("_display_name".equals(column)) {
                row.add("step8-usb-3gb.mp4");
            } else if ("_size".equals(column)) {
                row.add(VIRTUAL_LENGTH);
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    private synchronized File ensureSparseFixture() throws FileNotFoundException {
        final File cacheDir = getContext() != null ? getContext().getCacheDir() : null;
        if (cacheDir == null) {
            throw new FileNotFoundException("Step 8 test provider has no cache directory");
        }
        final File fixture = new File(cacheDir, "step8-usb-3gb-sparse.mp4");
        try (RandomAccessFile file = new RandomAccessFile(fixture, "rw")) {
            if (file.length() != VIRTUAL_LENGTH) {
                file.setLength(VIRTUAL_LENGTH);
            }
            file.seek(VERIFICATION_OFFSET);
            final byte[] verification = new byte[VERIFICATION_LENGTH];
            for (int index = 0; index < verification.length; index++) {
                verification[index] = (byte) ((VERIFICATION_OFFSET + index) & 0xff);
            }
            file.write(verification);
        } catch (IOException error) {
            final FileNotFoundException failure =
                    new FileNotFoundException("Unable to create Step 8 sparse USB fixture");
            failure.initCause(error);
            throw failure;
        }
        if (fixture.length() != VIRTUAL_LENGTH) {
            throw new FileNotFoundException(
                    "Sparse Step 8 fixture has wrong logical length: " + fixture.length());
        }
        return fixture;
    }
}
