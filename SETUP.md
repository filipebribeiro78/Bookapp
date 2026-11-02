# Setting up Estante

## 1. Connect your Google Sheet (about 5 minutes, easiest on a computer)

1. Create a new Google Sheet (open sheets.new) and give it a name, for example "Estante – catálogo".
2. In the Sheet, open **Extensions > Apps Script**. Delete the sample code, paste everything from
   `backend/apps-script/Code.gs`, and click **Save**.
3. Go back to the Sheet and reload the page. An **Estante** menu appears. Choose **Estante > 1. Set up this sheet**.
   Google asks you to authorise the script. Because it's your own script, Google warns "Google hasn't verified
   this app": click **Advanced**, then **Go to … (unsafe)**, and allow. The script only works on this Sheet and the
   "Estante photos" folder it creates in your Drive.
4. Back in Apps Script, click **Deploy > New deployment**, choose the type **Web app**, and set
   **Execute as: Me** and **Who has access: Anyone**. Click **Deploy** and copy the **Web app URL** (it ends in `/exec`).
5. In the Sheet, choose **Estante > 2. Show connection details** and copy the **token**.
6. On the phone, open Estante > **Settings**, paste the URL and the token, and tap **Save and test**.

"Who has access: Anyone" lets the app reach the script without a Google sign-in. The token is what stops anyone
else from writing to your Sheet, so keep it private.

When the script changes in a later version, paste the new `Code.gs`, then use
**Deploy > Manage deployments > Edit (pencil) > Version: New version > Deploy**. The URL stays the same.

## 2. Install the app on the phone

1. Download the APK on the phone.
2. Open it. Android asks you to allow installs from the app you opened it with (for example Files or Chrome):
   allow it.
3. Later versions install over the previous one, and your books and photos stay.
