package com.afollestad.materialdialogs.folderselector;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.webkit.MimeTypeMap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.R;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileChooserDialog extends DialogFragment implements MaterialDialog.ListCallback {

    private static final String DEFAULT_TAG = "[MD_FILE_SELECTOR]";

    private File parentFolder;
    private File[] parentContents;
    private boolean canGoUp = true;
    private FileCallback callback;

    public FileChooserDialog() {
    }

    CharSequence[] getContentsArray() {
        if (parentContents == null) {
            if (canGoUp) {
                return new String[]{getBuilder().goUpLabel};
            }
            return new String[]{};
        }
        String[] results = new String[parentContents.length + (canGoUp ? 1 : 0)];
        if (canGoUp) {
            results[0] = getBuilder().goUpLabel;
        }
        for (int i = 0; i < parentContents.length; i++) {
            results[canGoUp ? i + 1 : i] = parentContents[i].getName();
        }
        return results;
    }

    File[] listFiles(@Nullable String mimeType, @Nullable String[] extensions) {
        File[] contents = parentFolder.listFiles();
        List<File> results = new ArrayList<>();
        if (contents != null) {
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            for (File fi : contents) {
                if (fi.isDirectory()) {
                    results.add(fi);
                } else {
                    if (extensions != null) {
                        boolean found = false;
                        for (String ext : extensions) {
                            if (fi.getName().toLowerCase().endsWith(ext.toLowerCase())) {
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            results.add(fi);
                        }
                    } else if (mimeType != null) {
                        if (fileIsMimeType(fi, mimeType, mimeTypeMap)) {
                            results.add(fi);
                        }
                    }
                }
            }
            Collections.sort(results, new FileSorter());
            return results.toArray(new File[results.size()]);
        }
        return null;
    }

    boolean fileIsMimeType(File file, String mimeType, MimeTypeMap mimeTypeMap) {
        if (mimeType == null || mimeType.equals("*/*")) {
            return true;
        } else {
            // get the file mime type
            String filename = file.toURI().toString();
            int dotPos = filename.lastIndexOf('.');
            if (dotPos == -1) {
                return false;
            }
            String fileExtension = filename.substring(dotPos + 1);
            if (fileExtension.endsWith("json")) {
                return mimeType.startsWith("application/json");
            }
            String fileType = mimeTypeMap.getMimeTypeFromExtension(fileExtension);
            if (fileType == null) {
                return false;
            }
            // check the 'type/subtype' pattern
            if (fileType.equals(mimeType)) {
                return true;
            }
            // check the 'type/*' pattern
            int mimeTypeDelimiter = mimeType.lastIndexOf('/');
            if (mimeTypeDelimiter == -1) {
                return false;
            }
            String mimeTypeMainType = mimeType.substring(0, mimeTypeDelimiter);
            String mimeTypeSubtype = mimeType.substring(mimeTypeDelimiter + 1);
            if (!mimeTypeSubtype.equals("*")) {
                return false;
            }
            int fileTypeDelimiter = fileType.lastIndexOf('/');
            if (fileTypeDelimiter == -1) {
                return false;
            }
            String fileTypeMainType = fileType.substring(0, fileTypeDelimiter);
            if (fileTypeMainType.equals(mimeTypeMainType)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            return new MaterialDialog.Builder(getActivity())
                    .title(R.string.md_error_label)
                    .content(R.string.md_storage_perm_error)
                    .positiveText(android.R.string.ok)
                    .build();
        }

        if (getArguments() == null || !getArguments().containsKey("builder")) {
            throw new IllegalStateException("You must create a FileChooserDialog using the Builder.");
        }
        if (!getArguments().containsKey("current_path")) {
            getArguments().putString("current_path", getBuilder().initialPath);
        }
        parentFolder = new File(getArguments().getString("current_path"));
        checkIfCanGoUp();
        parentContents = listFiles(getBuilder().mimeType, getBuilder().extensions);
        return new MaterialDialog.Builder(getActivity())
                .title(parentFolder.getAbsolutePath())
                .typeface(getBuilder().mediumFont, getBuilder().regularFont)
                .items(getContentsArray())
                .itemsCallback(this)
                .onNegative(
                        new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                dialog.dismiss();
                            }
                        })
                .autoDismiss(false)
                .negativeText(getBuilder().cancelButton)
                .build();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (callback != null) {
            callback.onFileChooserDismissed(this);
        }
    }

    @Override
    public void onSelection(MaterialDialog materialDialog, View view, int i, CharSequence s) {
        if (canGoUp && i == 0) {
            parentFolder = parentFolder.getParentFile();
            if (parentFolder.getAbsolutePath().equals("/storage/emulated")) {
                parentFolder = parentFolder.getParentFile();
            }
            canGoUp = parentFolder.getParent() != null;
        } else {
            parentFolder = parentContents[canGoUp ? i - 1 : i];
            canGoUp = true;
            if (parentFolder.getAbsolutePath().equals("/storage/emulated")) {
                parentFolder = Environment.getExternalStorageDirectory();
            }
        }
        if (parentFolder.isFile()) {
            callback.onFileSelection(this, parentFolder);
            dismiss();
        } else {
            parentContents = listFiles(getBuilder().mimeType, getBuilder().extensions);
            MaterialDialog dialog = (MaterialDialog) getDialog();
            dialog.setTitle(parentFolder.getAbsolutePath());
            getArguments().putString("current_path", parentFolder.getAbsolutePath());
            dialog.setItems(getContentsArray());
        }
    }

    private void checkIfCanGoUp() {
        try {
            canGoUp = parentFolder.getPath().split("/").length > 1;
        } catch (IndexOutOfBoundsException e) {
            canGoUp = false;
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (getActivity() instanceof FileCallback) {
            callback = (FileCallback) getActivity();
        } else if (getParentFragment() instanceof FileCallback) {
            callback = (FileCallback) getParentFragment();
        } else {
            throw new IllegalStateException(
                    "FileChooserDialog needs to be shown from an Activity/Fragment implementing FileCallback.");
        }
    }

    public void show(FragmentManager fragmentManager) {
        final String tag = getBuilder().tag;
        Fragment frag = fragmentManager.findFragmentByTag(tag);
        if (frag != null) {
            ((DialogFragment) frag).dismiss();
            fragmentManager.beginTransaction().remove(frag).commit();
        }
        show(fragmentManager, tag);
    }

    public void show(FragmentActivity fragmentActivity) {
        show(fragmentActivity.getSupportFragmentManager());
    }

    @NonNull
    public String getInitialPath() {
        return getBuilder().initialPath;
    }

    @SuppressWarnings("ConstantConditions")
    @NonNull
    private Builder getBuilder() {
        return (Builder) getArguments().getSerializable("builder");
    }

    public interface FileCallback {

        void onFileSelection(@NonNull FileChooserDialog dialog, @NonNull File file);

        void onFileChooserDismissed(@NonNull FileChooserDialog dialog);
    }

    public static class Builder implements Serializable {

        @NonNull
        final transient Context context;
        @StringRes
        int cancelButton;
        String initialPath;
        String mimeType;
        String[] extensions;
        String tag;
        String goUpLabel;
        @Nullable
        String mediumFont;
        @Nullable
        String regularFont;

        public Builder(@NonNull Context context) {
            this.context = context;
            cancelButton = android.R.string.cancel;
            initialPath = Environment.getExternalStorageDirectory().getAbsolutePath();
            mimeType = null;
            goUpLabel = "...";
        }

        @NonNull
        public Builder typeface(@Nullable String medium, @Nullable String regular) {
            this.mediumFont = medium;
            this.regularFont = regular;
            return this;
        }

        @NonNull
        public Builder cancelButton(@StringRes int text) {
            cancelButton = text;
            return this;
        }

        @NonNull
        public Builder initialPath(@Nullable String initialPath) {
            if (initialPath == null) {
                initialPath = File.separator;
            }
            this.initialPath = initialPath;
            return this;
        }

        @NonNull
        public Builder mimeType(@Nullable String type) {
            mimeType = type;
            return this;
        }

        @NonNull
        public Builder extensionsFilter(@Nullable String... extensions) {
            this.extensions = extensions;
            return this;
        }

        @NonNull
        public Builder tag(@Nullable String tag) {
            if (tag == null) {
                tag = DEFAULT_TAG;
            }
            this.tag = tag;
            return this;
        }

        @NonNull
        public Builder goUpLabel(String text) {
            goUpLabel = text;
            return this;
        }

        @NonNull
        public FileChooserDialog build() {
            FileChooserDialog dialog = new FileChooserDialog();
            Bundle args = new Bundle();
            args.putSerializable("builder", this);
            dialog.setArguments(args);
            return dialog;
        }

        @NonNull
        public FileChooserDialog show(FragmentManager fragmentManager) {
            FileChooserDialog dialog = build();
            dialog.show(fragmentManager);
            return dialog;
        }

        @NonNull
        public FileChooserDialog show(FragmentActivity fragmentActivity) {
            return show(fragmentActivity.getSupportFragmentManager());
        }
    }

    private static class FileSorter implements Comparator<File> {

        @Override
        public int compare(File lhs, File rhs) {
            if (lhs.isDirectory() && !rhs.isDirectory()) {
                return -1;
            } else if (!lhs.isDirectory() && rhs.isDirectory()) {
                return 1;
            } else {
                return lhs.getName().compareTo(rhs.getName());
            }
        }
    }
}
