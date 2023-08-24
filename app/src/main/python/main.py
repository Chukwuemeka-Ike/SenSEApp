'''
Rensselaer Polytechnic Institute - Julius Lab
SenSE Project
Author - Chukwuemeka Osaretin Ike

Description:
'''
import itertools
import json
import numpy as np
import time

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
    # Take sequential combinations of the population's indexes, then randomize the rows
    # rng = np.random.default_rng()
    # combinations = np.array(list(itertools.combinations(range(1, 10 + 1), 2)))
    # labels = np.random.randint(1, len(combinations), len(combinations))
    # labels1 = rng.integers(1, len(combinations), len(combinations))
    # labels1 = rng.choice(combinations, len(combinations), replace=False)
    # labels2 = np.random.shuffle(combinations)
    # labels2 = rng.choice(len(combinations), len(combinations), replace=False)
    # labels3 = rng.choice(len(combinations), len(combinations), replace=False)

    # print(combinations)
    # print(labels)
    # print(labels1[:4])
    # print(labels2[:4])
    # print(labels3)
    L = np.array([0.03, 0.003, 0.]).reshape(3, 1)

    # print(simulateDynamics(inputData, L))
    startTime = time.time()
    print(simulateDynamics(inputData, L))
    # L = optimizeFilter(inputData)
    executionTime = (time.time() - startTime)
    print('Execution time in seconds: ' + str(executionTime))
    print(L)
    # print(type(L))


def simulateDynamics(t: np.ndarray, y: np.ndarray, L: np.ndarray) -> np.ndarray:
    '''
        Simulates the system dynamics using OBF class and returns the filter output
        Parameters:
            inputData (str) - JSON formatted string containing input data
            L (np.ndarray) - optimal gain matrix to use in simulating dynamics
        Returns:
            filterOutput(np.ndarray) - contains [xHat, yHat]:
                xHat (np.ndarray) - filter states
                yHat (np.ndarray) - filter output
    '''
    # # Get time and value vectors and create the state space before simulating the dynamics
    # t, y = parseUserData(inputData)     

    A, B, C, D = ObserverBasedFilter().createStateSpace(t, L)
    # print("Created state space")

    return ObserverBasedFilter().simulateDynamics(t, y, A, B, C, D)


def optimizeFilter(t: np.ndarray, y: np.ndarray) -> np.ndarray:
    '''Parses the input data for the time and value vectors, then optimizes
        the filter and returns the best gain vector.

    Parameters:
        inputData (str) -
    Returns: 
        L (np.ndarray) - optimal gain matrix to use in simulating
    '''
    # # Get time and value vectors
    # t, y = parseUserData(inputData)

    # Optimize the filter and return the optimal gains
    # np.array(t),np.array(y)
    return ObserverBasedFilter().optimizeFilter(t, y)


def estimateAverageDailyPhase(xHat1In: np.ndarray, xHat2In: np.ndarray, numDays: int,
                              numDaysOffset: int, numDataPointsPerDay: int) -> np.ndarray:
    '''
        Computes the average daily phase of the last numDays-numDaysOffset 
        Returns:
            avgDailyPhase (np.ndarray) - average daily phase
    '''
    xHat1 = np.array(xHat1In).reshape([1, numDays * numDataPointsPerDay])
    xHat2 = np.array(xHat2In).reshape([1, numDays * numDataPointsPerDay])
    # print(xHat1.shape)
    xHat1 = xHat1[:, numDaysOffset*numDataPointsPerDay:]
    xHat2 = xHat2[:, numDaysOffset*numDataPointsPerDay:]
    # print(xHat1.shape)
    averageDailyPhase = ObserverBasedFilter().estimateAverageDailyPhase(xHat1, xHat2, numDays-numDaysOffset,
                                                                        numDataPointsPerDay)
    # print(averageDailyPhase.shape)
    # print(averageDailyPhase)
    # averageDailyPhase = averageDailyPhase[:, numDaysOffset:]
    # print(averageDailyPhase)
    idx = np.argsort(averageDailyPhase)
    return np.append(averageDailyPhase, idx, axis=0)


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
    y = inputDataDict['y']
    t = inputDataDict['t']

    # for entry in inputDataDict[data_key][dataset_key]:
    #     times.append(entry['time'])
    #     y.append(float(entry['value']))

    # # Convert datetime strings to # minutes from first entry
    # timestamps = [(datetime.strptime(times[i], "%H:%M:%S")) for i in range(len(times)) ]
    # t = [(timestamps[i]-timestamps[0]).total_seconds()/seconds_in_hour for i in range(len(timestamps))]

    # print(t,y)
    t = np.array(t)
    y = np.array(y)

    return t, y


if __name__ == "__main__":
    f = open('C:/Users/chukw/Downloads/heart.json')

    main(f.read())
