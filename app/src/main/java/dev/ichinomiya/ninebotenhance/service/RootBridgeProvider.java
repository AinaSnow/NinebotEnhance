package dev.ichinomiya.ninebotenhance.service;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;

/** A one-use, nonce-authenticated bootstrap; no files, commands or display pixels are exposed. */
public final class RootBridgeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        if (uid != 2000) throw new SecurityException("需要已降权的 Root 辅助进程");
        return RootSession.get(getContext()).handshake(method, arg, extras == null ? new Bundle() : extras);
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new SecurityException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new SecurityException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new SecurityException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new SecurityException(); }
}
