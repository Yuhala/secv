

const size = 1000;
var array = new Array(size);


array.fill(Math.random());
const rand = () => String(Math.floor(Math.random() * size));


for (var i = 0; i < array.length; i++) {
    array[i] = rand();
}

function partition(arr, start, end) {
    const pivotVal = arr[end];
    let pivotIndex = start;
    for (let i = start; i < end; i++) {
        if (arr[i] < pivotVal) {
            [arr[i], arr[pivotIndex]] = [arr[pivotIndex], arr[i]];
            pivotIndex++;
        }
    }
    [arr[pivotIndex], arr[end]] = [arr[end], arr[pivotIndex]];
    return pivotIndex;
};

function quickSort(arr, start, end) {
    if (start >= end) {
        return;
    }
    let index = partition(arr, start, end);
    quickSort(arr, start, index - 1);
    quickSort(arr, index + 1, end);
}

quickSort(array, 0, array.length - 1);

function binarySearch(arr, value) {
    var guess,
        min = 0,
        max = arr.length - 1;

    while (min <= max) {
        guess = Math.floor((min + max) / 2);
        if (arr[guess] === value)
            return guess;
        else if (arr[guess] < value)
            min = guess + 1;
        else
            max = guess - 1;
    }

    return -1;
}


const N = 10000;

function testBinSearch() {
    var val = rand();
    return binarySearch(array, val);
}

function test(f) {
    let result;
    for (var i = 0; i < N; ++i) {
        result = f();
    }
}


var start = new Date();
test(testBinSearch);
var stop = new Date();
var diff = stop - start;
console.log(">>>>>>>>>>>> bin search end: time = ",diff," ms");





