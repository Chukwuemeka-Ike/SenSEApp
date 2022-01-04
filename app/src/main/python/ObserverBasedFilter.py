from math import pi, floor
import numpy as np
from scipy import signal
from scipy.fft import fft
import itertools

INT_MAX = 2147483647

class ObserverBasedFilter:
    # Filter parameters
    __omg = 2*pi/24
    __zeta = 0.25
    __gamma_d = 1
    __order = 1
    __stateLength = (2 * __order + 1)
    
    # Optimization hyperparameters
    __mu = 100
    __rho = 2
    __lambda = 50
    __max_iterations = 50

    # Bounds on the filter gains used for optimization
    __LB = -5
    __lStart = 0
    __rEnd = 8
    __mid = (__lStart+__rEnd)/2

    def simulateDynamics(self, t:np.ndarray, y: np.ndarray, L: np.ndarray) -> np.ndarray:
        '''
            Simulates the system dynamics on the input data
            Parameters:
                t (np.ndarray) - time values for the data
                y (np.ndarray) - biometric data values
                L (np.ndarray) - optimal gain matrix to use in simulating
            Returns:
                xHat (np.ndarray) - filter's state estimates 
                yHat (np.ndarray) - filter's output
        '''
        xHat = np.zeros([self.__stateLength, len(y)], dtype=float)
        xHat[:,0] = 0
        
        dt = t[1]-t[0]

        A, B, C, D = self.__createStateSpace(dt, L)

        for j in range(1, len(y)):
            xHat[:,j] = np.reshape(np.reshape(np.matmul(A,xHat[:,j-1]).T, (self.__stateLength,1)) + (B*y[j-1]), (self.__stateLength))
        yHat = np.matmul(C, xHat)

        return xHat, yHat

    def optimizeFilter(self, t:np.ndarray, y: np.ndarray) -> np.ndarray:
        '''
            Optimizes the filter given input time and value data
            Parameters:
                t (np.ndarray) - time values for the data
                y (np.ndarray) - biometric data values
            Returns:
                L (np.ndarray) - optimal gain matrix
        '''
        dt = t[1]-t[0]

        # # Testing a specific L against MATLAB values - correct
        # L = np.array([[0], [.0086], [.0339]])
        # A,B,C,D = self.__createStateSpace(dt, L)
        # print(A,B,C,D)
        # print(np.absolute(np.linalg.eig(A)[0]))
        # print(np.absolute(np.linalg.eig(A)[0]) > 1)
        # print(self.__checkStability(A))

        # Initialize population
        population = self.__initializePopulation()
        cost = np.zeros([self.__mu, 1], dtype=float)
        avgCost = np.zeros([self.__max_iterations, 1], dtype=float)
        newGen = np.zeros([self.__lambda, population.shape[1]])
        newCost = np.zeros([self.__lambda, 1])

        # Compute the original spectrum for calculating costs
        originalSpectrum, f, lent = self.__computeSpectrum(t, y)

        # Compute costs of initial population
        for i in range(0, self.__mu):
            L = population[i, :].reshape(self.__stateLength, 1)
            A, _, _, _ = self.__createStateSpace(dt, L)

            if not self.__checkStability(A):
                cost[i] = INT_MAX
                continue
            
            _, yHat = self.simulateDynamics(t, y, L)
            filteredSpectrum = self.__computeSpectrum(t, yHat)
            cost[i] = self.__computeCost(originalSpectrum, filteredSpectrum, f)
        
        # Run the optimization for __max_iterations
        for i in range(0, self.__max_iterations):
            # Take random combinations of the population's indexes, then randomize the rows
            combinations = np.array(list(itertools.combinations(range(1, self.__lambda + 1), self.__rho)))
            labels = np.random.randint(1, len(combinations), len(combinations))

            # Create __lambda new offspring and calculate their costs
            for j in range(self.__lambda):
                # Create offspring with a random pair from combinations
                newGen[j, :] = np.mean(population[combinations[labels[j], :], :], axis=0).reshape(1,self.__stateLength)
                L = newGen[j, :].reshape(self.__stateLength, 1)
                A, _, _, _ = self.__createStateSpace(dt, L)
                
                # Only simulateDynamics if the system is stable
                if not self.__checkStability(A):
                    newCost[j] = INT_MAX
                    continue
                
                _, yHat = self.simulateDynamics(t, y, L)

                # Compute the spectrum and 
                filteredSpectrum = self.__computeSpectrum(t, yHat)
                newCost[j] = self.__computeCost(originalSpectrum, filteredSpectrum, f)
            
            # Append the newGen and newCost to allow us work on one array each
            population = np.append(population, newGen, axis=0)
            cost = np.append(cost, newCost)

            # Remove the lambda highest costs
            maxIndex = np.argpartition(cost, -self.__lambda)[-self.__lambda:]
            
            cost = np.delete(cost, maxIndex)
            population = np.delete(population, maxIndex, axis=0)

            # Take the average cost - useful when trying to visualize
            avgCost[i] = np.mean(cost)

        # Return the best gain in the final population
        idx = np.argmin(cost)
        return population[idx, :].reshape(self.__stateLength, 1)

            
    def __createStateSpace(self, dt: float, L: np.ndarray):
        '''
            Creates discrete-time state space given the sampling time and gain matrix
            Parameters: 
                dt (float) - sampling time for discretization
                L (np.ndarray) - gain matrix f
            Returns:
                A (np.ndarray) - discrete-time A matrix
                B (np.ndarray) - discrete-time B matrix
                C (np.ndarray) - discrete-time C matrix
                D (np.ndarray) - discrete-time D matrix

        '''
        Ac = np.zeros([self.__stateLength, self.__stateLength], dtype = float)
        Bc = np.zeros([self.__stateLength, 1], dtype = float)
        Cc = np.zeros([1, self.__stateLength], dtype = float)
        Dc = 0

        # Populate State matrices in slices
        for k in range(self.__order):
            i = (1 + k) * 2
            k = k + 1 #handles the one off error
            Ac[i - 2:i, i - 2:i] = [[0, 1], [float(-(k * self.__omg) ** 2), 0]]
            Bc[i-2:i] = [[0],[(k*self.__omg)**2]]
            Cc[0][i-1] = (2 * self.__zeta) / (k * self.__omg)

        #Assigns edge values
        Bc[len(Bc)-1][0] = self.__gamma_d
        Cc[0][len(Cc[0])-1] = 1

        A = (Ac - L*Cc)
        B = L
        C = Cc
        D = Dc

        #uses the "scipy" library and converts the continuous matrixes into a SS
        #   -Then takes the continuous system and decritizes it (NOTE: method for c2d may not match model)
        discSystem = signal.StateSpace(A, B, C, D).to_discrete(dt)

        # Return the discrete system because we're only using discrete for the app 
        return discSystem.A, discSystem.B, discSystem.C, discSystem.D


    def __initializePopulation(self):
        '''
            Creates the initial population using a log scale
            Returns:
                population (np.ndarray) - the initial population to start the filter optimization
        '''
        N = (self.__rEnd-self.__lStart)*np.random.rand(self.__mu, self.__stateLength) + self.__lStart

        population = np.zeros(N.shape)
        population[N >= self.__mid] = 10**(N[N >= self.__mid] - self.__mid + self.__LB)
        population[N < self.__mid] = -10**(self.__mid - N[N < self.__mid] + self.__LB)
        
        return population
    
    def __computeSpectrum(self, t: np.ndarray, y: np.ndarray) -> np.ndarray:
        '''
            Computes the spectrum
            Parameters:
                t (np.ndarray) - time values for the data
                y (np.ndarray) - biometric data values
            Returns:
                P (np.ndarray) - 
                f (np.ndarray) -
                lent (int) - length of the time vector
        '''
        T = t[1] - t[0]
        Fs = 1/T
        lent = len(t)
        Y = fft(y)
        P2 = abs(Y / lent)
        P = P2[0:floor(lent/2)]
        P[1:len(P)-1] = 2*P[1:len(P)-1]
        f = Fs*np.arange(0, floor(lent/2))/lent

        return P, f, lent

    def __computeCost(self, originalSpectrum: np.ndarray, filteredSpectrum: np.ndarray, f: np.ndarray) -> float:
        '''
            Computes the cost of the filteredSpectrum by comparing it with the originalSpectrum
            Parameters:
                originalSpectrum (np.ndarray) - 
                filteredSpectrum (np.ndarray) - 
                f (np.ndarray) - 
            Returns:
                cost (float) - 
        '''
        N1 = np.argmin(abs(f - (1/24)))
        N2 = np.argmin(abs(f - (0.0289)))
        NN = N1 - N2
        
        # Calculate the square error within the band around each 
        # specified harmonic and the DC term
        J_harm = np.trapz(np.square((filteredSpectrum[0:NN] - originalSpectrum[0:NN]))) +\
                        np.trapz(np.square(filteredSpectrum[N1-NN:N1+NN+1] - originalSpectrum[N1-NN:N1+NN+1]))
        J_noise = np.trapz(np.square(filteredSpectrum[NN+1:N1-NN])) + np.trapz(np.square(originalSpectrum[N1+NN+1:]))

        return J_harm + J_noise
    
    def __checkStability(self, A) -> bool:
        '''
            Checks if the dynamics are stable by making sure the eigenvalues are in the 
            unit circle (assumes discrete-time system)
            Parameters:
                A (np.ndarray) - dynamics matrix
            Returns:
                isStable (bool) - whether the dynamics are stable or not
        '''
        if np.sum(np.absolute(np.linalg.eig(A)[0]) > 1) > 0:
            return False
        else:
            return True