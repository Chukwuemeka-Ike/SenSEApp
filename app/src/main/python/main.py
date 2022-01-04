import numpy as np
import json
from ObserverBasedFilter import ObserverBasedFilter
from datetime import datetime, timedelta


# s = '{"id":01, "name": "Emily", "language": ["C++", "Python"]}'
data_key = 'activities-heart-intraday'
dataset_key = 'dataset'

def main(inputData: str):
    '''

        Parameters:
            inputData (str) - JSON String containing the user data
            L (np.ndarray) -
    
    '''
    L = np.array([0.03, 0.003, 0.]).reshape(3,1)

    # print(simulateDynamics(inputData, L))
    L = optimizeFilter(inputData)
    print(L)
    print(type(L))

def simulateDynamics(inputData: str, L: np.ndarray) -> np.ndarray:
    '''
        Simulates the system dynamics using OBF class and returns the filter output
        Parameters:
            inputData (str) - 
            L (np.ndarray) - optimal gain matrix to use in simulating dynamics
        Returns:
            filterOutput(np.ndarray) - contains [xHat, yHat]:
                xHat (np.ndarray) - filter states
                yHat (np.ndarray) - filter output
    '''
    t, y = parseUserData(inputData)     # Get time and value vectors
    L = np.array(L).reshape(3, 1)       # Ensure L is an np array

    return ObserverBasedFilter().simulateDynamics(t, y, L)

def optimizeFilter(inputData: str) -> np.ndarray:
    '''
        Parses the input data for the time and value vectors, then optimizes 
        the filter and returns the best gain vector
        Parameters:
            inputData (str) -
        Returns: 
            L (np.ndarray) - optimal gain matrix to use in simulating
    '''
    # Get time and value vectors
    t, y = parseUserData(inputData)

    # Optimize the filter and return the optimal gains
    return ObserverBasedFilter().optimizeFilter(t, y).tolist()

def parseUserData(inputData: str) -> np.ndarray:
    '''
        Parses the input JSON string and return time and value arrays
        Parameters:     
            inputData (str) - 
        Returns: 
            t (np.ndarray) - time values for the data
            y (np.ndarray) - biometric data values
    '''
    # Total seconds in an hour
    seconds_in_hour = (timedelta(hours=1).total_seconds())

    inputDataDict = json.loads(inputData)

    times = []
    y = []
    for entry in inputDataDict[data_key][dataset_key]:
        times.append(entry['time'])
        y.append(float(entry['value']))

    # Convert datetime strings to # minutes from first entry
    timestamps = [(datetime.strptime(times[i], "%H:%M:%S")) for i in range(len(times)) ]
    t = [(timestamps[i]-timestamps[0]).total_seconds()/seconds_in_hour for i in range(len(timestamps))]

    t = np.array(t)
    y = np.array(y)

    return t, y

if __name__ == "__main__":
    f = open('C:/Users/chukw/Downloads/heart.json')
    
    main(f.read())