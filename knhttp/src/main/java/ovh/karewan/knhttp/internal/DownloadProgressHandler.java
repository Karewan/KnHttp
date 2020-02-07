/*
    KnHttp

    Copyright (c) 2019-2020 Florent VIALATTE
    Copyright (c) 2016-2019 Amit Shekhar

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
package ovh.karewan.knhttp.internal;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import ovh.karewan.knhttp.common.ANConstants;
import ovh.karewan.knhttp.interfaces.DownloadProgressListener;
import ovh.karewan.knhttp.model.Progress;

public final class DownloadProgressHandler extends Handler {

    private final DownloadProgressListener mDownloadProgressListener;

    public DownloadProgressHandler(DownloadProgressListener downloadProgressListener) {
        super(Looper.getMainLooper());
        mDownloadProgressListener = downloadProgressListener;
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg.what == ANConstants.UPDATE) {
            if (mDownloadProgressListener != null) {
                final Progress progress = (Progress) msg.obj;
                mDownloadProgressListener.onProgress(progress.currentBytes, progress.totalBytes);
            }
        } else {
            super.handleMessage(msg);
        }
    }
}
