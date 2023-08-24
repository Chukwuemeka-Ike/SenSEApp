'''
Rensselaer Polytechnic Institute - Julius Lab
SenSE Project
Author - Chukwuemeka Osaretin Ike

Description:
'''
import numpy as np
import FilterUtils

from datetime import datetime, timedelta
from SteadyStateKalmanFilter import SteadyStateKalmanFilter
from scipy.linalg import solve_discrete_are


def simulateDynamics(t: np.ndarray, y: np.ndarray, filterParams: np.ndarray) -> np.ndarray:
    '''Simulates the system dynamics and returns the filter outputs.
    Args:
        t:
        y:
        filterParams:
            Q: [0, :-1] - state covariance matrix.
            R: [0, -1] - output covariance value.
    Returns:
        filterOutput(np.ndarray) - contains [xHat; yHat]:
                xHat (np.ndarray) - filter states.
                yHat (np.ndarray) - filter output.
    '''
    SSKF = SteadyStateKalmanFilter()

    # Create the state space system.
    A, B, C, D = SSKF.createStateSpace(t)

    # Reshape Q as a matrix, then multiply by its transpose to give symmetry.
    Q = np.reshape(filterParams[0, :-1], (SSKF._stateLength, -1))
    Q = np.matmul(Q, Q.T)
    R = filterParams[0, -1]

    P = solve_discrete_are(A.T, C.T, Q, R)
    L = np.dot(A, np.dot(P, C.T))/(np.dot(C, np.dot(P, C.T)) + R)

    # Simulate the dynamics and return the result.
    return SSKF.simulateDynamics(A, C, L, y)

def optimizeFilter(t: np.ndarray, y: np.ndarray) -> np.ndarray:
    '''Optimizes the filter and returns the best parameters.

    Parameters:
        t:
    Returns: 
        filterParams: vector containing optimal Q and R values.
    '''
    SSKF = SteadyStateKalmanFilter()

    return SSKF.optimizeFilter(t, y)

def estimateAverageDailyPhase(
        xHat1In: np.ndarray, xHat2In: np.ndarray, numDays: int,
        numDaysOffset: int, numDataPointsPerDay: int
    ) -> np.ndarray:
    '''Computes the average daily phase of the last numDays-numDaysOffset.
        
        Returns:
            avgDailyPhase (np.ndarray) - average daily phase
    '''
    xHat1 = np.array(xHat1In).reshape([1, numDays * numDataPointsPerDay])
    xHat2 = np.array(xHat2In).reshape([1, numDays * numDataPointsPerDay])
    xHat1 = xHat1[:, numDaysOffset*numDataPointsPerDay:]
    xHat2 = xHat2[:, numDaysOffset*numDataPointsPerDay:]

    averageDailyPhase = FilterUtils.estimateAverageDailyPhase(
        xHat1, xHat2, numDays-numDaysOffset, numDataPointsPerDay
    )

    idx = np.argsort(averageDailyPhase)
    return np.append(averageDailyPhase, idx, axis=0)