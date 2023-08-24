'''
Rensselaer Polytechnic Institute - Julius Lab
SenSE Project
Author - Chukwuemeka Osaretin Ike

Description:
'''
import itertools
import numpy as np
import FilterUtils

from scipy import signal
from scipy.linalg import solve_discrete_are
from typing import Tuple


INT_MAX = 2147483647


class SteadyStateKalmanFilter:
    '''Steady-State Kalman Filter class for running and optimizing the SSKF.

    It returns filter outputs and optimized gains as needed in the Kotlin code.
    '''
    # Filter parameters.
    _omg = 2*np.pi/24
    _order = 1
    _stateLength = (2 * _order + 1)

    # Optimization hyperparameters
    _max_iterations = 25
    _mu = 100
    _lambda = 50
    _rho = 2

    # Bounds on the parameters used for optimization.
    _qLB = -5      # Q lower bound - 10^qLB.
    _qLEnd = 0     # Q lower end.
    _qREnd = 18    # Twice the number of orders of magnitudes.
    _rLB = 1e2     # R lower bound.
    _rUB = 1e8     # R upper bound.

    def initializePopulation(self) -> Tuple[np.ndarray]:
        '''Creates the initial Q and R populations for the optimization.
        Returns:
            Q_pop: population of Q covariance matrices.
            R_pop: population of R covariance values.
        '''
        qSize = self._stateLength**2
        mid = (self._qREnd + self._qLEnd)/2
        diff = 0.5

        N = (self._qREnd-self._qLEnd)*np.random.rand(self._mu, qSize) + self._qLEnd
        Q_pop = np.zeros(N.shape)
        Q_pop[N > mid+diff] = 10**(N[N > mid+diff] - mid + self._qLB)
        Q_pop[N < mid-diff] = -10**(mid - N[N < mid-diff] + self._qLB)

        R_pop = (self._rLB + (self._rUB - self._rLB)*np.random.rand(self._mu, 1))

        return Q_pop, R_pop

    def createStateSpace(self, t: np.ndarray) -> Tuple[np.ndarray]:
        '''Creates discrete-time state space given the time vector.

        Args: 
            t: time (in hours from first entry) values for the data
        Returns:
            A: discrete-time A matrix
            B: discrete-time B matrix
            C: discrete-time C matrix
            D: discrete-time D matrix
        '''
        A = np.zeros([self._stateLength, self._stateLength])
        B = np.zeros([self._stateLength, 1])
        C = np.zeros([1, self._stateLength])
        D = 0

        # Populate the matrices.
        for k in range(self._order):
            i = (1+k)*2
            A[i-2:i, i-2:i] = [[0, 1], [float(-((k+1)*self._omg)**2), 0]]
            C[0][i-1] = (2/((k+1)*self._omg))
        C[0][-1] = 1

        # Use scipy to convert the continuous matrices above to discrete.
        dt = t[1] - t[0] # Sample time.
        discSystem = signal.StateSpace(A, B, C, D).to_discrete(dt)

        # Return the discrete system matrices.
        return discSystem.A, discSystem.B, discSystem.C, discSystem.D

    def simulateDynamics(self, A: np.ndarray, C: np.ndarray,
                         L: np.ndarray, y: np.ndarray
        ) -> np.ndarray:
        '''Simulates the system dynamics on the input data [y].
        Args:
            A: discrete-time A matrix.
            C: discrete-time C matrix.
            L (np.ndarray) - gain matrix 
            y: biometric data values.
        Returns:
            filterOutput(np.ndarray) - contains [xHat; yHat]:
                xHat (np.ndarray) - filter state estimates 
                yHat (np.ndarray) - filter output
        '''
        inputLength = len(y)
        xHat = np.zeros([self._stateLength, inputLength])
        xHat[-1, 0] = np.mean(y) # Set the first bias term to the mean y value.

        # Run the system step-by-step. If a y value is zero, run autonomously.
        for i in range(1, inputLength):
            # The reshapes are all to make sure it's the right shape.
            if y[i] == 0:
                xHat[:, i] = np.reshape(np.dot(A, xHat[:, i-1]), (self._stateLength))
            else:
                xHat[:, i] = np.reshape(np.dot((A - np.dot(L, C)), xHat[:, i-1]), (-1)) +\
                      np.reshape(L*y[i-1], (-1))

        # Multiply the filter state evolution by the C matrix to give us the output.
        yHat = np.matmul(C, xHat)

        return np.append(xHat, yHat, axis=0)


    def optimizeFilter(self, time: np.ndarray, y: np.ndarray) -> np.ndarray:
        '''Optimizes the filter given input time and biometric data.

        Args:
            time: time (in hours from first entry) for the data.
            y: biometric data.
        Returns:
            L: optimal gain matrix
        '''
        # Random number generator for randomizing the combinations of population members.
        rng = np.random.default_rng()
        Q_pop, R_pop = self.initializePopulation()

        Cost = np.zeros([self._mu, 1])
        # avgCost = np.zeros([self._max_iterations, 1])
        newQGen = np.zeros([self._lambda, Q_pop.shape[1]])
        newRGen = np.zeros([self._lambda, 1])
        newCost = np.zeros([self._lambda, 1])

        # Generate sequential combinations of [1:mu]
        combinations = np.array(list(itertools.combinations(range(0, self._mu ), self._rho)))
        # print(combinations.shape)

        # Compute the original spectrum for calculating costs of filter outputs.
        originalSpectrum, f, _ = FilterUtils.computeSpectrum(time, y)

        A, B, C, D = self.createStateSpace(time)

        # Compute costs of the initial population.
        for member in range(self._mu):
            Q = Q_pop[member, :].reshape(self._stateLength, -1)
            Q = np.matmul(Q, Q.T)
            R = R_pop[member]

            try:
                P = solve_discrete_are(A.T, C.T, Q, R)
            except:
                Cost[member] = INT_MAX
                continue
            # print(member)

            if P.size == 0:
                Cost[member] = INT_MAX
                continue
            L = np.dot(A, np.dot(P, C.T))/(np.dot(C, np.dot(P, C.T)) + R)
            out = self.simulateDynamics(A, C, L, y)
            yHat = out[-1, :]

            # Compute the spectrum and corresponding cost.
            filteredSpectrum, _, _ = FilterUtils.computeSpectrum(time, yHat)
            Cost[member] = FilterUtils.computeCost(originalSpectrum, filteredSpectrum, f, self._order)
        
        # Run the optimization for _max_iterations.
        for iteration in range(self._max_iterations):
            # Randomize the rows of combinations
            labels = rng.choice(len(combinations), len(combinations), replace=False)

            # Create _lambda new offspring and calculate their costs
            for j in range(self._lambda):
                # Create offspring with a random pair from combinations.
                newQGen[j, :] = np.mean(Q_pop[combinations[labels[j], :], :], axis=0).reshape(1, -1)
                newRGen[j] = np.mean(R_pop[combinations[labels[j], :]], axis=0).reshape(1, -1)

                Q = newQGen[j, :].reshape(self._stateLength, -1)
                Q = np.matmul(Q, Q.T)
                R = newRGen[j]

                try:
                    P = solve_discrete_are(A.T, C.T, Q, R)
                except:
                    newCost[j] = INT_MAX
                    continue

                L = np.dot(A, np.dot(P, C.T))/(np.dot(C, np.dot(P, C.T)) + R)
                out = self.simulateDynamics(A, C, L, y)
                yHat = out[-1, :]

                # Compute the spectrum and corresponding cost.
                filteredSpectrum, _, _ = FilterUtils.computeSpectrum(time, yHat)
                newCost[j] = FilterUtils.computeCost(originalSpectrum, filteredSpectrum, f, self._order)
            
            # Append the newGen and newCost to allow us work on one array each.
            Q_pop = np.append(Q_pop, newQGen, axis=0)
            R_pop = np.append(R_pop, newRGen, axis=0)
            Cost = np.append(Cost, newCost)

            # Remove the lambda highest costs from both cost and population.
            maxIndex = np.argpartition(Cost, -self._lambda)[-self._lambda:]
            
            Cost = np.delete(Cost, maxIndex)
            Q_pop = np.delete(Q_pop, maxIndex, axis=0)
            R_pop = np.delete(R_pop, maxIndex, axis=0)

            # # Take the average cost - useful when trying to visualize.
            # avgCost[iteration] = np.mean(cost)

        # Return the best performer in the final population.
        idx = np.argmin(Cost)
        return np.append(Q_pop[idx, :].reshape(1, -1), R_pop[idx].reshape(1, 1), axis=1)