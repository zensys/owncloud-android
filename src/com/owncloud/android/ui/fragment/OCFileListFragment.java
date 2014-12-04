/* ownCloud Android client application
 *   Copyright (C) 2011  Bartek Przybylski
 *   Copyright (C) 2012-2014 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package com.owncloud.android.ui.fragment;

import java.io.File;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;

import cz.zensys.owncloud.android.R;
import com.owncloud.android.datamodel.FileDataStorageManager;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.files.FileMenuFilter;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.ui.activity.FileDisplayActivity;
import com.owncloud.android.ui.activity.MoveActivity;
import com.owncloud.android.ui.activity.OnEnforceableRefreshListener;
import com.owncloud.android.ui.adapter.FileListListAdapter;
import com.owncloud.android.ui.dialog.ConfirmationDialogFragment;
import com.owncloud.android.ui.dialog.RemoveFileDialogFragment;
import com.owncloud.android.ui.dialog.RenameFileDialogFragment;
import com.owncloud.android.ui.preview.PreviewImageFragment;
import com.owncloud.android.ui.preview.PreviewMediaFragment;

/**
 * A Fragment that lists all files and folders in a given path.
 * 
 * TODO refactorize to get rid of direct dependency on FileDisplayActivity
 * 
 * @author Bartek Przybylski
 * @author masensio
 * @author David A. Velasco
 */
public class OCFileListFragment extends ExtendedListFragment {
    
    private static final String TAG = OCFileListFragment.class.getSimpleName();

    private static final String MY_PACKAGE = OCFileListFragment.class.getPackage() != null ?
            OCFileListFragment.class.getPackage().getName() : "com.owncloud.android.ui.fragment";
            
    public final static String ARG_JUST_FOLDERS = MY_PACKAGE + ".JUST_FOLDERS";
    public final static String ARG_ALLOW_CONTEXTUAL_ACTIONS = MY_PACKAGE + ".ALLOW_CONTEXTUAL";
            
    private static final String KEY_FILE = MY_PACKAGE + ".extra.FILE";

    private FileFragment.ContainerActivity mContainerActivity;
   
    private OCFile mFile = null;
    private FileListListAdapter mAdapter;
    
    private OCFile mTargetFile;

    
    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log_OC.e(TAG, "onAttach");
        try {
            mContainerActivity = (FileFragment.ContainerActivity) activity;
            
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement " + 
                    FileFragment.ContainerActivity.class.getSimpleName());
        }
        try {
            setOnRefreshListener((OnEnforceableRefreshListener) activity);
            
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement " + 
                    SwipeRefreshLayout.OnRefreshListener.class.getSimpleName());
        }
    }

    
    @Override
    public void onDetach() {
        setOnRefreshListener(null);
        mContainerActivity = null;
        super.onDetach();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log_OC.e(TAG, "onActivityCreated() start");
        
        if (savedInstanceState != null) {
            mFile = savedInstanceState.getParcelable(KEY_FILE);
        }
        
        Bundle args = getArguments();
        boolean justFolders = (args == null) ? false : args.getBoolean(ARG_JUST_FOLDERS, false); 
        mAdapter = new FileListListAdapter(
                justFolders,
                getSherlockActivity(), 
                mContainerActivity
        );
        setListAdapter(mAdapter);
        
        registerForContextMenu(getListView());
        getListView().setOnCreateContextMenuListener(this);
  }
    
    /**
     * Saves the current listed folder.
     */
    @Override
    public void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_FILE, mFile);
    }
    
    /**
     * Call this, when the user presses the up button.
     * 
     * Tries to move up the current folder one level. If the parent folder was removed from the 
     * database, it continues browsing up until finding an existing folders.
     * 
     * return       Count of folder levels browsed up.
     */
    public int onBrowseUp() {
        OCFile parentDir = null;
        int moveCount = 0;
        
        if(mFile != null){
            FileDataStorageManager storageManager = mContainerActivity.getStorageManager();
            
            String parentPath = null;
            if (mFile.getParentId() != FileDataStorageManager.ROOT_PARENT_ID) {
                parentPath = new File(mFile.getRemotePath()).getParent();
                parentPath = parentPath.endsWith(OCFile.PATH_SEPARATOR) ? parentPath : 
                	parentPath + OCFile.PATH_SEPARATOR;
                parentDir = storageManager.getFileByPath(parentPath);
                moveCount++;
            } else {
                parentDir = storageManager.getFileByPath(OCFile.ROOT_PATH);
            }
            while (parentDir == null) {
                parentPath = new File(parentPath).getParent();
                parentPath = parentPath.endsWith(OCFile.PATH_SEPARATOR) ? parentPath : 
                	parentPath + OCFile.PATH_SEPARATOR;
                parentDir = storageManager.getFileByPath(parentPath);
                moveCount++;
            }   // exit is granted because storageManager.getFileByPath("/") never returns null
            mFile = parentDir;
            
            listDirectory(mFile);

            onRefresh(false);
            
            // restore index and top position
            restoreIndexAndTopPosition();
            
        }   // else - should never happen now
   
        return moveCount;
    }
    
    @Override
    public void onItemClick(AdapterView<?> l, View v, int position, long id) {
        OCFile file = (OCFile) mAdapter.getItem(position);
        if (file != null) {
            if (file.isFolder()) { 
                // update state and view of this fragment
                listDirectory(file);
                // then, notify parent activity to let it update its state and view
                mContainerActivity.onBrowsedDownTo(file);
                // save index and top position
                saveIndexAndTopPosition(position);
                
            } else { /// Click on a file
                if (PreviewImageFragment.canBePreviewed(file)) {
                    // preview image - it handles the download, if needed
                    ((FileDisplayActivity)mContainerActivity).startImagePreview(file);
                    
                } else if (file.isDown()) {
                    if (PreviewMediaFragment.canBePreviewed(file)) {
                        // media preview
                        ((FileDisplayActivity)mContainerActivity).startMediaPreview(file, 0, true);
                    } else {
                        mContainerActivity.getFileOperationsHelper().openFile(file);
                    }
                    
                } else {
                    // automatic download, preview on finish
                    ((FileDisplayActivity)mContainerActivity).startDownloadForPreview(file);
                }
                    
            }
            
        } else {
            Log_OC.d(TAG, "Null object in ListAdapter!!");
        }
        
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu (
            ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        Bundle args = getArguments();
        boolean allowContextualActions = 
                (args == null) ? true : args.getBoolean(ARG_ALLOW_CONTEXTUAL_ACTIONS, true); 
        if (allowContextualActions) {
            MenuInflater inflater = getSherlockActivity().getMenuInflater();
            inflater.inflate(R.menu.file_actions_menu, menu);
            AdapterContextMenuInfo info = (AdapterContextMenuInfo) menuInfo;
            OCFile targetFile = (OCFile) mAdapter.getItem(info.position);
            
            if (mContainerActivity.getStorageManager() != null) {
                FileMenuFilter mf = new FileMenuFilter(
                    targetFile,
                    mContainerActivity.getStorageManager().getAccount(),
                    mContainerActivity,
                    getSherlockActivity()
                );
                mf.filter(menu);
            }
            
            /// additional restrictions for this fragment 
            // TODO allow in the future 'open with' for previewable files
            MenuItem item = menu.findItem(R.id.action_open_file_with);
            if (item != null) {
                item.setVisible(false);
                item.setEnabled(false);
            }
            /// TODO break this direct dependency on FileDisplayActivity... if possible
            FileFragment frag = ((FileDisplayActivity)getSherlockActivity()).getSecondFragment();
            if (frag != null && frag instanceof FileDetailFragment && 
                    frag.getFile().getFileId() == targetFile.getFileId()) {
                item = menu.findItem(R.id.action_see_details);
                if (item != null) {
                    item.setVisible(false);
                    item.setEnabled(false);
                }
            }
        }
    }
    
    
    /**
     * {@inhericDoc}
     */
    @Override
    public boolean onContextItemSelected (MenuItem item) {
        AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();        
        mTargetFile = (OCFile) mAdapter.getItem(info.position);
        switch (item.getItemId()) {                
            case R.id.action_share_file: {
                mContainerActivity.getFileOperationsHelper().shareFileWithLink(mTargetFile);
                return true;
            }
            case R.id.action_unshare_file: {
                mContainerActivity.getFileOperationsHelper().unshareFileWithLink(mTargetFile);
                return true;
            }
            case R.id.action_rename_file: {
                RenameFileDialogFragment dialog = RenameFileDialogFragment.newInstance(mTargetFile);
                dialog.show(getFragmentManager(), FileDetailFragment.FTAG_RENAME_FILE);
                return true;
            }
            case R.id.action_remove_file: {
                RemoveFileDialogFragment dialog = RemoveFileDialogFragment.newInstance(mTargetFile);
                dialog.show(getFragmentManager(), ConfirmationDialogFragment.FTAG_CONFIRMATION);
                return true;
            }
            case R.id.action_download_file: 
            case R.id.action_sync_file: {
                mContainerActivity.getFileOperationsHelper().syncFile(mTargetFile);
                return true;
            }
            case R.id.action_cancel_download:
            case R.id.action_cancel_upload: {
                ((FileDisplayActivity)mContainerActivity).cancelTransference(mTargetFile);
                return true;
            }
            case R.id.action_see_details: {
                mContainerActivity.showDetails(mTargetFile);
                return true;
            }
            case R.id.action_send_file: {
                // Obtain the file
                if (!mTargetFile.isDown()) {  // Download the file
                    Log_OC.d(TAG, mTargetFile.getRemotePath() + " : File must be downloaded");
                    ((FileDisplayActivity)mContainerActivity).startDownloadForSending(mTargetFile);
                    
                } else {
                    mContainerActivity.getFileOperationsHelper().sendDownloadedFile(mTargetFile);
                }
                return true;
            }
            case R.id.action_move: {
                Intent action = new Intent(getActivity(), MoveActivity.class);

                // Pass mTargetFile that contains info of selected file/folder
                action.putExtra(MoveActivity.EXTRA_TARGET_FILE, mTargetFile);
                getActivity().startActivityForResult(action, FileDisplayActivity.ACTION_MOVE_FILES);
                return true;
            }
            default:
                return super.onContextItemSelected(item); 
        }
    }


    /**
     * Use this to query the {@link OCFile} that is currently
     * being displayed by this fragment
     * @return The currently viewed OCFile
     */
    public OCFile getCurrentFile(){
        return mFile;
    }
    
    /**
     * Calls {@link OCFileListFragment#listDirectory(OCFile)} with a null parameter
     */
    public void listDirectory(){
        listDirectory(null);
    }
    
    /**
     * Lists the given directory on the view. When the input parameter is null,
     * it will either refresh the last known directory. list the root
     * if there never was a directory.
     * 
     * @param directory File to be listed
     */
    public void listDirectory(OCFile directory) {
        FileDataStorageManager storageManager = mContainerActivity.getStorageManager();
        if (storageManager != null) {

            // Check input parameters for null
            if(directory == null){
                if(mFile != null){
                    directory = mFile;
                } else {
                    directory = storageManager.getFileByPath("/");
                    if (directory == null) return; // no files, wait for sync
                }
            }
        
        
            // If that's not a directory -> List its parent
            if(!directory.isFolder()){
                Log_OC.w(TAG, "You see, that is not a directory -> " + directory.toString());
                directory = storageManager.getFileById(directory.getParentId());
            }

            mAdapter.swapDirectory(directory, storageManager);
            if (mFile == null || !mFile.equals(directory)) {
                mList.setSelectionFromTop(0, 0);
            }
            mFile = directory;
        }
    }

}
