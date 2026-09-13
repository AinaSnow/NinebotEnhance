package dev.ichinomiya.ninebotenhance.service;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import dev.ichinomiya.ninebotenhance.core.LogArchive;
import dev.ichinomiya.ninebotenhance.diagnostics.Diagnostics;
import dev.ichinomiya.ninebotenhance.ipc.Protocol;
import java.io.*;
import java.util.Date;

/** Called only after FrameBridgeService has authenticated and retained the original caller UID. */
final class LogExport {
    static Bundle dispatch(Context context, int code, int uid, Bundle args, String previousExit) {
        LogArchive archive = LogShareProvider.archive(context); Bundle result = new Bundle();
        try {
            if (code == Protocol.LOG_EXPORT_BEGIN) {
                LogArchive.Ticket ticket = archive.begin(uid, "Ninebot Enhance " + Protocol.VERSION + " 完整日志\n导出时间：" + new Date()
                        + "\n包含两个进程当前保留的日志；进程重启前或环形缓冲区已淘汰的事件不在本文件中。\n\n模块进程日志：\n"
                        + "pid=" + Process.myPid() + "\n系统记录（可能早于本次测试）：\n" + previousExit + "\n" + Diagnostics.text() + "\n\n", System.currentTimeMillis());
                try {
                    result.putParcelable("log_fd", ParcelFileDescriptor.open(ticket.file(), ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND));
                    result.putString("log_token", ticket.token());
                } catch (IOException e) { archive.cancel(uid, ticket.token()); throw e; }
            } else if (code == Protocol.LOG_EXPORT_FINISH) {
                File file = archive.commit(uid, args.getString("log_token"), System.currentTimeMillis());
                Uri uri = LogShareProvider.uri(file);
                // The injected UI is Ninebot's UID. Give it this one URI so it can pass read access to a chosen recipient.
                context.grantUriPermission(uid == Process.myUid() ? Protocol.MODULE : Protocol.TARGET, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                result.putParcelable("log_uri", uri);
            } else archive.cancel(uid, args.getString("log_token"));
            return result;
        } catch (IOException e) { throw new IllegalStateException("日志文件生成失败：" + e.getMessage()); }
    }
    private LogExport() {}
}
