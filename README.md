# SenSE App

Early development stage.

The SenSE app is an Android implementation of the observer-based filter (OBF) developed by the 
Julius Lab in Rensselaer Polytechnic Institute.

The filter is able to process user biometric data and extract the underlying circadian signal, which
can be used in subsequent control attempts.

The long-term goals of the application are to create a pipeline for crowd-sourcing useful data
for our research, as well as test the underlying algorithm in diverse situations. 

## Current Bugs (06/04/2022)
### (Somewhat) Daily Updates 
I use [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) to schedule the daily filter updates and weekly  optimizations (every 7th update), but these updates do not run as expected on every device. This unwanted behavior is triggered when the app is closed from the *recents* screen. It's a known [issue](https://issuetracker.google.com/issues/110745313) that multiple OEMs trigger a force stop when apps are closed from *recents* instead of the Stock Android approach. 

However, at this time, I don't have a workaround. I simply have the Worker enqueue another update when it fails. That second run is triggered whenever the app is opened again by the user. It's rather hacky, but 🤷🏿‍♂️.